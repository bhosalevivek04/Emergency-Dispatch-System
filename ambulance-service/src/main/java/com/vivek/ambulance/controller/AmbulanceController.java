package com.vivek.ambulance.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.ambulance.dto.AmbulanceLocationEvent;
import com.vivek.ambulance.dto.AmbulanceRegistrationRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/ambulance")
@RequiredArgsConstructor
public class AmbulanceController {

	private final KafkaTemplate<String, AmbulanceLocationEvent> kafkaTemplate;

	/**
	 * Test endpoint to register/update ambulance
	 * In production, ambulances register through a proper authentication system
	 */
	@PostMapping
	public ResponseEntity<String> registerAmbulance(@RequestBody AmbulanceRegistrationRequest request) {
		log.info("Registering ambulance: {} at lat={}, lon={}", request.getAmbulanceId(),
				request.getLatitude(), request.getLongitude());

		// Create location event
		AmbulanceLocationEvent event = AmbulanceLocationEvent.builder()
				.ambulanceId(request.getAmbulanceId())
				.latitude(request.getLatitude())
				.longitude(request.getLongitude())
				.speed(0.0)
				.heading(0.0)
				.timestamp(System.currentTimeMillis())
				.build();

		// Send initial location to Kafka
		kafkaTemplate.send("ambulance-location-topic", event.getAmbulanceId(), event);

		return ResponseEntity.ok("Ambulance registered: " + request.getAmbulanceId());
	}
}
