package com.vivek.notification.listener;

import java.time.Instant;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
	private final RestTemplate restTemplate = new RestTemplate();

	@Value("${notification.webhook.enabled:false}")
	private boolean webhookEnabled;

	@Value("${notification.webhook.url:}")
	private String webhookUrl;

	@KafkaListener(topics = "ambulance-assigned-topic", groupId = "notification-group")
	public void consumeAssignment(String message) {
		try {
			AssignmentEvent event = objectMapper.readValue(message, AssignmentEvent.class);
			meterRegistry.counter("notification.assignments.consumed.total").increment();
			boolean sent = sendNotification(event);
			if (sent) {
				meterRegistry.counter("notification.sent.total").increment();
				log.info("Notification sent emergencyId={} ambulanceId={} distanceKm={} version={}",
						event.getEmergencyId(), event.getAmbulanceId(), event.getDistanceKm(), event.getVersion());
			} else {
				meterRegistry.counter("notification.skipped.total").increment();
				log.warn("Notification not sent (provider disabled or not configured) emergencyId={} ambulanceId={}",
						event.getEmergencyId(), event.getAmbulanceId());
			}
		} catch (Exception ex) {
			meterRegistry.counter("notification.parse_error.total").increment();
			log.error("Failed to parse or process assignment notification: {}", message, ex);
		}
	}

	private boolean sendNotification(AssignmentEvent event) {
		if (!webhookEnabled || webhookUrl == null || webhookUrl.isBlank()) {
			return false;
		}

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);

			Map<String, Object> payload = Map.of(
					"eventType", "AMBULANCE_ASSIGNED",
					"emergencyId", event.getEmergencyId(),
					"ambulanceId", event.getAmbulanceId(),
					"distanceKm", event.getDistanceKm(),
					"version", event.getVersion(),
					"timestamp", Instant.now().toString());
			restTemplate.postForEntity(webhookUrl, new HttpEntity<>(payload, headers), String.class);
			return true;
		} catch (Exception ex) {
			meterRegistry.counter("notification.send_error.total").increment();
			log.error("Webhook notification failed emergencyId={} ambulanceId={} webhookUrl={}",
					event.getEmergencyId(), event.getAmbulanceId(), webhookUrl, ex);
			return false;
		}
	}
}
