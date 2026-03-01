package com.vivek.ambulance.listener;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.ambulance.dto.AssignmentEvent;
import com.vivek.ambulance.dto.CompletionEvent;
import com.vivek.ambulance.model.AmbulanceStatus;
import com.vivek.ambulance.service.AmbulanceProducer;
import com.vivek.ambulance.service.AmbulanceStateTracker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class AmbulanceAssignmentListener {
	private final ObjectMapper objectMapper;
	private final AmbulanceProducer ambulanceProducer;
	private final AmbulanceStateTracker ambulanceStateTracker;
	private final StringRedisTemplate redisTemplate;
	private final MeterRegistry meterRegistry;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	@KafkaListener(topics = "ambulance-assigned-topic", groupId = "ambulance-driver-group")
	public void consumeAssignment(String message) throws JsonProcessingException {
		AssignmentEvent assignment = objectMapper.readValue(message, AssignmentEvent.class);
		meterRegistry.counter("ambulance.assignments.consumed.total").increment();
		String ambulanceId = assignment.getAmbulanceId();
		String emergencyId = assignment.getEmergencyId();
		String assignmentKey = assignment.getEmergencyId() + "-" + ambulanceId;

		if (isDuplicate("assignment", assignmentKey)) {
			meterRegistry.counter("ambulance.assignments.duplicate.total").increment();
			log.info("Duplicate assignment ignored assignmentKey={}", assignmentKey);
			return;
		}

		AmbulanceStatus currentStatus = ambulanceStateTracker.getStatus(ambulanceId);
		long currentVersion = ambulanceStateTracker.getVersion(ambulanceId);
		String activeEmergencyId = redisTemplate.opsForValue().get(activeEmergencyKey(ambulanceId));
		if (currentStatus == AmbulanceStatus.ASSIGNED && emergencyId.equals(activeEmergencyId)) {
			log.info(
					"Duplicate assignment ignored for same incident ambulanceId={} emergencyId={} currentVersion={}",
					ambulanceId, emergencyId, currentVersion);
			return;
		}

		long assignedExpectedVersion = assignment.getVersion();
		boolean assigned = ambulanceStateTracker.assignEmergencyAtomically(ambulanceId, emergencyId,
				assignedExpectedVersion);
		if (!assigned) {
			meterRegistry.counter("ambulance.assignments.rejected.total").increment();
			log.warn(
					"Assignment rejected by FSM assignmentKey={} ambulanceId={} emergencyId={} currentStatus={} currentVersion={} incomingVersion={}",
					assignmentKey, ambulanceId, emergencyId, currentStatus, currentVersion, assignedExpectedVersion);
			return;
		}

		scheduler.schedule(() -> transitionToOnRoute(ambulanceId, assignedExpectedVersion + 1), 3, TimeUnit.SECONDS);
		scheduler.schedule(() -> transitionToArrived(ambulanceId, assignedExpectedVersion + 2), 5, TimeUnit.SECONDS);
		scheduler.schedule(() -> completeTrip(assignment, assignedExpectedVersion + 3), 8, TimeUnit.SECONDS);
	}

	private void transitionToOnRoute(String ambulanceId, long expectedVersion) {
		ambulanceStateTracker.transition(ambulanceId, AmbulanceStatus.ON_ROUTE, expectedVersion);
	}

	private void transitionToArrived(String ambulanceId, long expectedVersion) {
		ambulanceStateTracker.transition(ambulanceId, AmbulanceStatus.ARRIVED, expectedVersion);
	}

	private void completeTrip(AssignmentEvent assignment, long expectedVersion) {
		String ambulanceId = assignment.getAmbulanceId();
		boolean completed = ambulanceStateTracker.transition(ambulanceId, AmbulanceStatus.COMPLETED, expectedVersion);
		if (!completed) {
			return;
		}

		long completionVersion = ambulanceStateTracker.getVersion(ambulanceId);
		CompletionEvent completion = new CompletionEvent(ambulanceId, assignment.getEmergencyId(), "COMPLETED",
				completionVersion);
		ambulanceProducer.sendCompletion(completion);
		meterRegistry.counter("ambulance.completions.published.total").increment();
		redisTemplate.delete(activeEmergencyKey(ambulanceId));

		ambulanceStateTracker.transition(ambulanceId, AmbulanceStatus.AVAILABLE, completionVersion);
		log.info("Completion published ambulanceId={} emergencyId={} completionVersion={}", ambulanceId,
				assignment.getEmergencyId(), completionVersion);
	}

	@PreDestroy
	public void shutdown() {
		scheduler.shutdown();
	}

	private boolean isDuplicate(String type, String key) {
		String redisKey = "idempotency:" + type + ":" + key;
		Boolean created = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", Duration.ofMinutes(10));
		return !Boolean.TRUE.equals(created);
	}

	private String activeEmergencyKey(String ambulanceId) {
		return "ambulance:" + ambulanceId + ":activeEmergencyId";
	}
}
