package com.vivek.ambulance.service;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.vivek.ambulance.dto.AmbulanceLocationEvent;
import com.vivek.ambulance.dto.CompletionEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AmbulanceProducer {
	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final MeterRegistry meterRegistry;
	private static final String LOCATION_TOPIC = "ambulance-location-topic";
	private static final String COMPLETION_TOPIC = "ambulance-completed-topic";

	public void sendLocation(AmbulanceLocationEvent event) {
		kafkaTemplate.send(LOCATION_TOPIC, event.getAmbulanceId(), event);
		meterRegistry.counter("ambulance.locations.published.total").increment();
	}

	public void sendCompletion(CompletionEvent event) {
		kafkaTemplate.send(COMPLETION_TOPIC, event.getAmbulanceId(), event);
		meterRegistry.counter("ambulance.completions.kafka_published.total").increment();
	}
}
