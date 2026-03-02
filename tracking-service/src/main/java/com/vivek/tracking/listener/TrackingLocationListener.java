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

@Component
@Slf4j
@RequiredArgsConstructor
public class TrackingLocationListener {

	private final ObjectMapper objectMapper;
	private final TrackingCacheService trackingCacheService;
	private final SimpMessagingTemplate messagingTemplate;
	private final MeterRegistry meterRegistry;

	// toggle console logging of each broadcast
	@org.springframework.beans.factory.annotation.Value("${tracking.listener.log.enabled:true}")
	private boolean logEnabled;

	@KafkaListener(topics = "ambulance-location-topic", groupId = "tracking-group")
	public void consumeAmbulanceLocation(String message) throws JsonProcessingException {
		AmbulanceLocationEvent event = objectMapper.readValue(message, AmbulanceLocationEvent.class);
		trackingCacheService.saveLatest(event);
		messagingTemplate.convertAndSend("/topic/location", event);
		meterRegistry.counter("tracking.location.consumed.total").increment();
		meterRegistry.counter("tracking.websocket.broadcast.total").increment();
		if (logEnabled) {
			log.info("Broadcast location ambulanceId={} latitude={} longitude={}", event.getAmbulanceId(),
					event.getLatitude(), event.getLongitude());
		}
	}
}
