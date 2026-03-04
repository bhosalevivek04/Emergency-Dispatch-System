package com.vivek.ambulance.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.ambulance.dto.AmbulanceLocationEvent;
import com.vivek.ambulance.dto.AmbulanceRegistrationRequest;
import com.vivek.ambulance.model.AmbulanceStatus;
import com.vivek.ambulance.service.AmbulanceStateTracker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/ambulance")
@RequiredArgsConstructor
public class AmbulanceController {

	private final KafkaTemplate<String, AmbulanceLocationEvent> kafkaTemplate;
	private final AmbulanceStateTracker stateTracker;
	
	@Value("${ambulance.fleet.ids:AMB-101,AMB-102,AMB-103}")
	private String fleetIdsConfig;

	/**
	 * Get list of available ambulances
	 * Used by dispatchers to see which ambulances are free
	 */
	@GetMapping("/available")
	@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
	public ResponseEntity<Map<String, Object>> getAvailableAmbulances() {
		String[] ambulanceIds = fleetIdsConfig.split(",");
		List<String> available = new ArrayList<>();
		
		for (String ambulanceId : ambulanceIds) {
			ambulanceId = ambulanceId.trim();
			if (stateTracker.isAvailable(ambulanceId)) {
				available.add(ambulanceId);
			}
		}
		
		Map<String, Object> response = new HashMap<>();
		response.put("available", available);
		response.put("total", ambulanceIds.length);
		response.put("availableCount", available.size());
		
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Get status of all ambulances in fleet
	 * Used by dispatchers and admins to monitor fleet
	 */
	@GetMapping("/fleet")
	@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
	public ResponseEntity<Map<String, Object>> getFleetStatus() {
		String[] ambulanceIds = fleetIdsConfig.split(",");
		Map<String, Map<String, Object>> fleet = new HashMap<>();
		
		for (String ambulanceId : ambulanceIds) {
			ambulanceId = ambulanceId.trim();
			AmbulanceStatus status = stateTracker.getStatus(ambulanceId);
			long version = stateTracker.getVersion(ambulanceId);
			
			Map<String, Object> ambulanceInfo = new HashMap<>();
			ambulanceInfo.put("status", status != null ? status.name() : "UNKNOWN");
			ambulanceInfo.put("version", version);
			ambulanceInfo.put("available", stateTracker.isAvailable(ambulanceId));
			
			fleet.put(ambulanceId, ambulanceInfo);
		}
		
		Map<String, Object> response = new HashMap<>();
		response.put("fleet", fleet);
		response.put("total", ambulanceIds.length);
		
		return ResponseEntity.ok(response);
	}

	/**
	 * Test endpoint to register/update ambulance
	 * In production, ambulances register through a proper authentication system
	 */
	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'AMBULANCE_DRIVER')")
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
