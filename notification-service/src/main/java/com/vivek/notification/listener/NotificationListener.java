package com.vivek.notification.listener;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.notification.dto.AssignmentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationListener {
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;

	@KafkaListener(topics = "ambulance-assigned-topic", groupId = "notification-group")
	public void consumeAssignment(String message) throws Exception {
		try {
			AssignmentEvent event = objectMapper.readValue(message, AssignmentEvent.class);
			meterRegistry.counter("notification.assignments.consumed.total").increment();
			meterRegistry.counter("notification.sent.total").increment();
			log.info("Notification sent emergencyId={} ambulanceId={} distanceKm={} version={}", event.getEmergencyId(),
					event.getAmbulanceId(), event.getDistanceKm(), event.getVersion());
		} catch (Exception ex) {
			meterRegistry.counter("notification.consume.failures.total").increment();
			throw ex;
		}
	}
}
