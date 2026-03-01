package com.vivek.emergency.service;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.vivek.emergency.dto.EmergencyEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmergencyProducer {
	private final KafkaTemplate<String, EmergencyEvent> kafkaTemplate;
	private final MeterRegistry meterRegistry;
	private static final String TOPIC = "emergency-topic";

	public void sendEmergency(EmergencyEvent event) {
		kafkaTemplate.send(TOPIC, event.getEmergencyId(), event);
		meterRegistry.counter("emergency.published.total").increment();
	}
}
