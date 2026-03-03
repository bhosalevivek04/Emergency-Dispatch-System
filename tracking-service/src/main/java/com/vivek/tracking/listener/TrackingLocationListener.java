package com.vivek.tracking.listener;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.tracking.dto.AmbulanceLocationEvent;
import com.vivek.tracking.service.TrackingCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrackingLocationListener {

	private final ObjectMapper objectMapper;
	private final TrackingCacheService trackingCacheService;
	private final SimpMessagingTemplate messagingTemplate;
	private final MeterRegistry meterRegistry;

	// toggle console logging of each broadcast
	@Value("${tracking.listener.log.enabled:true}")
	private boolean logEnabled;

	@KafkaListener(topics = "ambulance-location-topic", groupId = "tracking-group")
	public void consumeAmbulanceLocation(String message) {
		try {
			AmbulanceLocationEvent event = objectMapper.readValue(message, AmbulanceLocationEvent.class);
			trackingCacheService.saveLatest(event);
			messagingTemplate.convertAndSend("/topic/location", event);
			meterRegistry.counter("tracking.location.consumed.total").increment();
			meterRegistry.counter("tracking.websocket.broadcast.total").increment();
			if (logEnabled) {
				log.debug("Broadcast location ambulanceId={} latitude={} longitude={}", event.getAmbulanceId(),
						event.getLatitude(), event.getLongitude());
			}
		} catch (JsonProcessingException e) {
			meterRegistry.counter("tracking.location.parse_error.total").increment();
			log.error("Failed to parse ambulance location message: {}", message, e);
		} catch (Exception e) {
			meterRegistry.counter("tracking.location.processing_error.total").increment();
			log.error("Failed to process ambulance location: {}", message, e);
		}
	}
}
