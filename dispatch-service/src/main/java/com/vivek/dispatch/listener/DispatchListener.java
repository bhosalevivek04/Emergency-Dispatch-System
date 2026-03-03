package com.vivek.dispatch.listener;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.dispatch.dto.AmbulanceLocationEvent;
import com.vivek.dispatch.dto.CompletionEvent;
import com.vivek.dispatch.dto.EmergencyEvent;
import com.vivek.dispatch.service.DispatchEngine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class DispatchListener {
	private final DispatchEngine dispatchEngine;
	private final ObjectMapper objectMapper;
	private final StringRedisTemplate redisTemplate;
	private final MeterRegistry meterRegistry;

	@KafkaListener(topics = "emergency-topic", groupId = "dispatch-group")
	public void consumerEmergency(String message) {
		// DO NOT propagate JsonProcessingException — Spring Kafka would retry-loop forever on bad JSON
		try {
			EmergencyEvent emergency = objectMapper.readValue(message, EmergencyEvent.class);
			meterRegistry.counter("dispatch.topic.emergency.consumed.total").increment();
			dispatchEngine.handleEmergency(emergency);
		} catch (JsonProcessingException e) {
			// Malformed message — log and discard. DLT handles it via KafkaDltConfig.
			meterRegistry.counter("dispatch.topic.emergency.parse_error.total").increment();
			log.error("Malformed emergency message — discarding (will go to DLT): {}", message, e);
		}
	}

	@KafkaListener(topics = "ambulance-location-topic", groupId = "dispatch-group")
	public void consumeAmbulance(String message) {
		try {
			AmbulanceLocationEvent ambulance = objectMapper.readValue(message, AmbulanceLocationEvent.class);
			meterRegistry.counter("dispatch.topic.ambulance_location.consumed.total").increment();
			dispatchEngine.handleAmbulanceUpdate(ambulance);
		} catch (JsonProcessingException e) {
			meterRegistry.counter("dispatch.topic.ambulance_location.parse_error.total").increment();
			log.error("Malformed ambulance-location message — discarding: {}", message, e);
		}
	}

	@KafkaListener(topics = "ambulance-completed-topic", groupId = "dispatch-group")
	public void consumeCompletion(String message) {
		try {
			CompletionEvent completion = objectMapper.readValue(message, CompletionEvent.class);
			String completionKey = completion.getEmergencyId() + "-" + completion.getAmbulanceId();
			if (isDuplicate("completion", completionKey)) {
				meterRegistry.counter("dispatch.topic.completion.duplicate.total").increment();
				log.info("Duplicate completion ignored completionKey={}", completionKey);
				return;
			}
			meterRegistry.counter("dispatch.topic.completion.consumed.total").increment();
			dispatchEngine.markAmbulanceAvailable(completion.getAmbulanceId(), completion.getVersion());
		} catch (JsonProcessingException e) {
			meterRegistry.counter("dispatch.topic.completion.parse_error.total").increment();
			log.error("Malformed completion message — discarding: {}", message, e);
		}
	}

	private boolean isDuplicate(String type, String key) {
		String redisKey = "idempotency:" + type + ":" + key;
		Boolean created = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", Duration.ofMinutes(10));
		return !Boolean.TRUE.equals(created);
	}
}
