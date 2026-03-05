package com.vivek.ambulance.service;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.vivek.ambulance.dto.AmbulanceLocationEvent;
import com.vivek.ambulance.dto.AmbulanceStatusEvent;
import com.vivek.ambulance.dto.CompletionEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AmbulanceProducer {
	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final MeterRegistry meterRegistry;
	private static final String LOCATION_TOPIC = "ambulance-location-topic";
	private static final String COMPLETION_TOPIC = "ambulance-completed-topic";
	private static final String STATUS_TOPIC = "ambulance-status-topic";

	public void sendLocation(AmbulanceLocationEvent event) {
		kafkaTemplate.send(LOCATION_TOPIC, event.getAmbulanceId(), event);
		meterRegistry.counter("ambulance.locations.published.total").increment();
	}

	public void sendCompletion(CompletionEvent event) {
		kafkaTemplate.send(COMPLETION_TOPIC, event.getAmbulanceId(), event);
		meterRegistry.counter("ambulance.completions.kafka_published.total").increment();
	}

	public void sendStatus(AmbulanceStatusEvent event) {
		kafkaTemplate.send(STATUS_TOPIC, event.getAmbulanceId(), event);
		meterRegistry.counter("ambulance.status.kafka_published.total", "status", event.getStatus()).increment();
	}
	
	public void sendAcknowledgment(String topic, String emergencyId, String ambulanceId, String status, long version) {
		com.vivek.ambulance.dto.AssignmentAckEvent ack = new com.vivek.ambulance.dto.AssignmentAckEvent(
			emergencyId, ambulanceId, status, version, System.currentTimeMillis()
		);
		kafkaTemplate.send(topic, emergencyId, ack);
		meterRegistry.counter("ambulance.acks.published.total").increment();
	}
}
