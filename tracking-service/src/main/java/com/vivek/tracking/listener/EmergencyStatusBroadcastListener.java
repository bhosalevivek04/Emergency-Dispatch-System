package com.vivek.tracking.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.tracking.dto.EmergencyStatusUpdateEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmergencyStatusBroadcastListener {

	private final ObjectMapper objectMapper;
	private final SimpMessagingTemplate messagingTemplate;
	private final MeterRegistry meterRegistry;

	@KafkaListener(topics = "ambulance-assigned-topic", groupId = "tracking-group")
	public void handleAssigned(String message) {
		broadcastStatus(message, "ASSIGNED");
	}

	@KafkaListener(topics = "ambulance-completed-topic", groupId = "tracking-group")
	public void handleCompleted(String message) {
		broadcastStatus(message, "COMPLETED");
	}

	private void broadcastStatus(String message, String fallbackStatus) {
		try {
			JsonNode node = objectMapper.readTree(message);
			String emergencyId = getText(node, "emergencyId");
			String ambulanceId = getText(node, "ambulanceId");
			String status = getText(node, "status");
			if (status == null || status.isBlank()) {
				status = fallbackStatus;
			}

			if (emergencyId == null || emergencyId.isBlank()) {
				log.warn("Skipping emergency status broadcast due to missing emergencyId payload={}", message);
				return;
			}

			EmergencyStatusUpdateEvent event = EmergencyStatusUpdateEvent.builder()
					.emergencyId(emergencyId)
					.status(status)
					.ambulanceId(ambulanceId)
					.timestamp(System.currentTimeMillis())
					.build();
			messagingTemplate.convertAndSend("/topic/emergencies", event);
			meterRegistry.counter("tracking.emergency_status.broadcast.total", "status", status).increment();
		} catch (Exception ex) {
			meterRegistry.counter("tracking.emergency_status.parse_error.total").increment();
			log.error("Failed to broadcast emergency status update payload={}", message, ex);
		}
	}

	private String getText(JsonNode node, String fieldName) {
		JsonNode value = node.get(fieldName);
		return value == null || value.isNull() ? null : value.asText();
	}
}
