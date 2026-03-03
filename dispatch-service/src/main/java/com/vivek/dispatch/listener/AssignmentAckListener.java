package com.vivek.dispatch.listener;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.dispatch.dto.AssignmentAckEvent;
import com.vivek.dispatch.service.DispatchEngine;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class AssignmentAckListener {
	private final ObjectMapper objectMapper;
	private final StringRedisTemplate redisTemplate;
	private final MeterRegistry meterRegistry;
	private final DispatchEngine dispatchEngine;

	@KafkaListener(topics = "ambulance-assignment-ack-topic", groupId = "dispatch-ack-group")
	public void consumeAck(String message) {
		try {
			AssignmentAckEvent ack = objectMapper.readValue(message, AssignmentAckEvent.class);
			
			String ackKey = "assignment:ack:" + ack.getEmergencyId();
			redisTemplate.opsForValue().set(ackKey, ack.getStatus());
			
			if ("ACCEPTED".equals(ack.getStatus())) {
				meterRegistry.counter("dispatch.assignment.ack.accepted.total").increment();
				log.info("Assignment acknowledged emergencyId={} ambulanceId={} status=ACCEPTED", 
					ack.getEmergencyId(), ack.getAmbulanceId());
			} else {
				meterRegistry.counter("dispatch.assignment.ack.rejected.total").increment();
				log.warn("Assignment rejected by ambulance emergencyId={} ambulanceId={} status=REJECTED", 
					ack.getEmergencyId(), ack.getAmbulanceId());
				
				// Re-queue emergency for re-dispatch with another ambulance
				dispatchEngine.requeueEmergency(ack.getEmergencyId());
			}
		} catch (JsonProcessingException e) {
			log.error("Failed to parse acknowledgment message, skipping: {}", message, e);
			// Don't rethrow - let the message be skipped
		} catch (Exception e) {
			log.error("Error processing acknowledgment message: {}", message, e);
			throw new RuntimeException("Failed to process acknowledgment", e);
		}
	}
}
