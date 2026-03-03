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
import com.vivek.ambulance.service.AmbulanceMovementSimulator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class AmbulanceAssignmentListener {
	private final ObjectMapper objectMapper;
	private final AmbulanceProducer ambulanceProducer;
	private final AmbulanceStateTracker ambulanceStateTracker;
	private final AmbulanceMovementSimulator movementSimulator;
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
			
			// Send REJECTED ACK to dispatch service
			sendAssignmentAck(emergencyId, ambulanceId, "REJECTED", assignedExpectedVersion);
			return;
		}
		
		// Send ACCEPTED ACK to dispatch service
		sendAssignmentAck(emergencyId, ambulanceId, "ACCEPTED", assignedExpectedVersion + 1);

		// Set destination for movement simulator from assignment event
		double estimatedDurationSeconds = 0;
		if (assignment.getEmergencyLat() != 0 && assignment.getEmergencyLon() != 0) {
			estimatedDurationSeconds = movementSimulator.setDestination(ambulanceId, assignment.getEmergencyLat(), assignment.getEmergencyLon());
			log.info("Ambulance {} will move to emergency at ({}, {}), ETA: {} minutes", 
				ambulanceId, assignment.getEmergencyLat(), assignment.getEmergencyLon(), estimatedDurationSeconds / 60.0);
		}
		
		// Calculate dynamic state transition times based on actual route duration
		// If no duration available (OSRM failed), use default timings
		long onRouteDelay, arrivedDelay, completedDelay;
		
		if (estimatedDurationSeconds > 0) {
			// ON_ROUTE: 10% of journey time (ambulance starts moving)
			onRouteDelay = Math.max(2, (long)(estimatedDurationSeconds * 0.1));
			
			// ARRIVED: 90% of journey time (ambulance reaches destination)
			arrivedDelay = Math.max(5, (long)(estimatedDurationSeconds * 0.9));
			
			// COMPLETED: Full journey time + 30 seconds for patient loading
			completedDelay = (long)(estimatedDurationSeconds + 30);
			
			log.info("Dynamic state transitions for {}: ON_ROUTE in {}s, ARRIVED in {}s, COMPLETED in {}s",
				ambulanceId, onRouteDelay, arrivedDelay, completedDelay);
		} else {
			// Fallback to default timings - use longer delays for demo visibility
			// Get ambulance current location from movement simulator
			var currentLocation = movementSimulator.getCurrentLocation(ambulanceId);
			if (currentLocation != null && assignment.getEmergencyLat() != 0 && assignment.getEmergencyLon() != 0) {
				double straightLineDistance = calculateStraightLineDistance(
					currentLocation.lat, currentLocation.lon,
					assignment.getEmergencyLat(), assignment.getEmergencyLon()
				);
				
				// Assume 30 km/h average speed for estimation
				double estimatedMinutes = (straightLineDistance / 30.0) * 60.0;
				long estimatedSeconds = Math.max(60, (long)(estimatedMinutes * 60));
				
				onRouteDelay = Math.max(5, (long)(estimatedSeconds * 0.1));
				arrivedDelay = Math.max(30, (long)(estimatedSeconds * 0.9));
				completedDelay = estimatedSeconds + 30;
				
				log.info("Using fallback state transitions for {} (no OSRM route): distance={}km, ON_ROUTE in {}s, ARRIVED in {}s, COMPLETED in {}s",
					ambulanceId, String.format("%.2f", straightLineDistance), onRouteDelay, arrivedDelay, completedDelay);
			} else {
				// Ultimate fallback - use minimum delays
				onRouteDelay = 5;
				arrivedDelay = 60;
				completedDelay = 120;
				log.info("Using minimum fallback state transitions for {} (no location data)", ambulanceId);
			}
		}

		scheduler.schedule(() -> transitionToOnRoute(ambulanceId, assignedExpectedVersion + 1), onRouteDelay, TimeUnit.SECONDS);
		scheduler.schedule(() -> transitionToArrived(ambulanceId, assignedExpectedVersion + 2), arrivedDelay, TimeUnit.SECONDS);
		scheduler.schedule(() -> completeTrip(assignment, assignedExpectedVersion + 3), completedDelay, TimeUnit.SECONDS);
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

		// Clear destination - ambulance can now patrol
		movementSimulator.clearDestination(ambulanceId);

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
	
	private double calculateStraightLineDistance(double lat1, double lon1, double lat2, double lon2) {
		final int R = 6371; // Earth radius in km
		double latDistance = Math.toRadians(lat2 - lat1);
		double lonDistance = Math.toRadians(lon2 - lon1);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return R * c;
	}
	
	private void sendAssignmentAck(String emergencyId, String ambulanceId, String status, long version) {
		try {
			ambulanceProducer.sendAcknowledgment("ambulance-assignment-ack-topic", emergencyId, ambulanceId, status, version);
			log.info("Assignment ACK sent emergencyId={} ambulanceId={} status={}", emergencyId, ambulanceId, status);
		} catch (Exception e) {
			log.error("Failed to send assignment ACK emergencyId={} ambulanceId={}", emergencyId, ambulanceId, e);
		}
	}
}
