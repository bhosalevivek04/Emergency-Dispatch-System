package com.vivek.emergency.service;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.vivek.emergency.dto.EmergencyEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyProducer {
	private final KafkaTemplate<String, EmergencyEvent> kafkaTemplate;
	private final MeterRegistry meterRegistry;
	private static final String TOPIC = "emergency-topic";

	public void sendEmergency(EmergencyEvent event) {
		try {
			kafkaTemplate.send(TOPIC, event.getEmergencyId(), event).get(5, java.util.concurrent.TimeUnit.SECONDS);
			log.info("Emergency published to Kafka successfully: {}", event.getEmergencyId());
			meterRegistry.counter("emergency.published.total").increment();
		} catch (Exception e) {
			log.error("Failed to publish emergency to Kafka: {}", event.getEmergencyId(), e);
			meterRegistry.counter("emergency.kafka.publish.failed").increment();
			throw new RuntimeException("Kafka publish failed", e);
		}
	}
}
