package com.vivek.ambulance.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.ambulance.dto.AmbulanceLocationEvent;
import com.vivek.ambulance.dto.AmbulanceRegistrationRequest;
import com.vivek.ambulance.entity.Ambulance;
import com.vivek.ambulance.model.AmbulanceStatus;
import com.vivek.ambulance.service.AmbulanceMovementSimulator;
import com.vivek.ambulance.service.AmbulancePersistenceService;
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
	private final AmbulancePersistenceService ambulancePersistenceService;
	private final AmbulanceMovementSimulator movementSimulator;

	@Value("${ambulance.fleet.ids:AMB-101,AMB-102,AMB-103}")
	private String fleetIdsConfig;

	/**
	 * Get list of available ambulances
	 * Used by dispatchers to see which ambulances are free
	 */
	@GetMapping("/available")
	@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
	public ResponseEntity<Map<String, Object>> getAvailableAmbulances() {
		List<String> ambulanceIds = getKnownAmbulanceIds();
		List<String> available = new ArrayList<>();

		for (String ambulanceId : ambulanceIds) {
			if (stateTracker.isAvailable(ambulanceId)) {
				available.add(ambulanceId);
			}
		}

		Map<String, Object> response = new HashMap<>();
		response.put("available", available);
		response.put("total", ambulanceIds.size());
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
		List<String> ambulanceIds = getKnownAmbulanceIds();
		Map<String, Map<String, Object>> fleet = new HashMap<>();

		for (String ambulanceId : ambulanceIds) {
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
		response.put("total", ambulanceIds.size());

		return ResponseEntity.ok(response);
	}

	@GetMapping("/{ambulanceId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'AMBULANCE_DRIVER')")
	public ResponseEntity<Map<String, Object>> getAmbulance(@PathVariable String ambulanceId) {
		AmbulanceStatus status = stateTracker.getStatus(ambulanceId);
		long version = stateTracker.getVersion(ambulanceId);
		if (status == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(Map.of(
				"id", ambulanceId,
				"status", status.name(),
				"version", version,
				"available", stateTracker.isAvailable(ambulanceId)));
	}

	/**
	 * Test endpoint to register/update ambulance
	 * In production, ambulances register through a proper authentication system
	 */
	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'AMBULANCE_DRIVER')")
	public ResponseEntity<String> registerAmbulance(@RequestBody AmbulanceRegistrationRequest request) {
		if (request.getAmbulanceId() == null || request.getAmbulanceId().isBlank()) {
			return ResponseEntity.badRequest().body("ambulanceId is required");
		}

		log.info("Registering ambulance: {} at lat={}, lon={}", request.getAmbulanceId(),
				request.getLatitude(), request.getLongitude());
		double latitude = request.getLatitude() != null ? request.getLatitude() : 18.5204;
		double longitude = request.getLongitude() != null ? request.getLongitude() : 73.8567;

		Ambulance ambulance = new Ambulance();
		ambulance.setAmbulanceId(request.getAmbulanceId());
		ambulance.setCoordinates(latitude, longitude);
		ambulance.setStatus(request.getStatus() == null || request.getStatus().isBlank()
				? AmbulanceStatus.AVAILABLE.name()
				: request.getStatus());
		ambulancePersistenceService.saveOrUpdate(ambulance);
		// Initializes redis keys for newly discovered ambulances as needed.
		stateTracker.isAvailable(request.getAmbulanceId());
		// Register into movement simulator so dispatch keeps receiving periodic updates.
		movementSimulator.registerAmbulance(request.getAmbulanceId(), latitude, longitude);

		// Create location event
		AmbulanceLocationEvent event = AmbulanceLocationEvent.builder()
				.ambulanceId(request.getAmbulanceId())
				.latitude(latitude)
				.longitude(longitude)
				.speed(0.0)
				.heading(0.0)
				.timestamp(System.currentTimeMillis())
				.build();

		// Send initial location to Kafka
		kafkaTemplate.send("ambulance-location-topic", event.getAmbulanceId(), event);

		return ResponseEntity.ok("Ambulance registered: " + request.getAmbulanceId());
	}

	private List<String> getKnownAmbulanceIds() {
		Set<String> knownIds = new LinkedHashSet<>();

		for (String ambulanceId : fleetIdsConfig.split(",")) {
			if (ambulanceId != null && !ambulanceId.isBlank()) {
				knownIds.add(ambulanceId.trim());
			}
		}

		for (Ambulance ambulance : ambulancePersistenceService.findAll()) {
			if (ambulance.getAmbulanceId() != null && !ambulance.getAmbulanceId().isBlank()) {
				knownIds.add(ambulance.getAmbulanceId().trim());
			}
		}

		return new ArrayList<>(knownIds);
	}
}
