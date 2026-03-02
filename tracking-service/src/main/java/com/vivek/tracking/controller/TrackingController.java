package com.vivek.tracking.controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.tracking.dto.AmbulanceLocationEvent;
import com.vivek.tracking.service.TrackingCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
public class TrackingController {

	private final TrackingCacheService trackingCacheService;
	private final KafkaTemplate<String, AmbulanceLocationEvent> kafkaTemplate;

	// Rate limiting: Track last update time per ambulance
	private final Map<String, Long> lastUpdateTime = new ConcurrentHashMap<>();
	private static final long MIN_UPDATE_INTERVAL_MS = 2000; // 2 seconds minimum between updates

	@GetMapping("/ambulances")
	public ResponseEntity<Map<String, AmbulanceLocationEvent>> getAllLatest() {
		return ResponseEntity.ok(trackingCacheService.getAllLatest());
	}

	@GetMapping("/ambulances/{ambulanceId}")
	public ResponseEntity<AmbulanceLocationEvent> getLatest(@PathVariable String ambulanceId) {
		AmbulanceLocationEvent location = trackingCacheService.getLatest(ambulanceId);
		if (location == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(location);
	}

	/**
	 * Endpoint for mobile driver app to send location updates
	 * Includes rate limiting to prevent overwhelming the system
	 */
	@PostMapping("/location")
	public ResponseEntity<Map<String, Object>> updateLocation(@RequestBody AmbulanceLocationEvent location) {
		
		// Validate input
		if (location.getAmbulanceId() == null || location.getAmbulanceId().isEmpty()) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Ambulance ID is required"));
		}

		// Validate coordinate ranges (primitives can't be null, just check ranges)
		if (location.getLatitude() < -90 || location.getLatitude() > 90 ||
			location.getLongitude() < -180 || location.getLongitude() > 180) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Invalid coordinates"));
		}

		// Rate limiting check
		String ambulanceId = location.getAmbulanceId();
		long currentTime = System.currentTimeMillis();
		Long lastUpdate = lastUpdateTime.get(ambulanceId);

		if (lastUpdate != null && (currentTime - lastUpdate) < MIN_UPDATE_INTERVAL_MS) {
			// Too frequent, but return success to avoid client errors
			return ResponseEntity.ok(Map.of(
					"status", "throttled",
					"message", "Update received but throttled",
					"nextUpdateIn", MIN_UPDATE_INTERVAL_MS - (currentTime - lastUpdate)
			));
		}

		// Update last update time
		lastUpdateTime.put(ambulanceId, currentTime);

		// Set timestamp if not provided (0 means not set)
		if (location.getTimestamp() == 0) {
			location.setTimestamp(currentTime);
		}

		// Send to Kafka (async, non-blocking)
		try {
			kafkaTemplate.send("ambulance-location-topic", ambulanceId, location);
			
			log.debug("Location update sent to Kafka: {} at ({}, {})", 
					ambulanceId, location.getLatitude(), location.getLongitude());

			return ResponseEntity.ok(Map.of(
					"status", "success",
					"message", "Location update sent",
					"ambulanceId", ambulanceId,
					"timestamp", currentTime
			));
		} catch (Exception e) {
			log.error("Error sending location to Kafka: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to send location update"));
		}
	}

	/**
	 * Health check endpoint
	 */
	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		return ResponseEntity.ok(Map.of(
				"status", "UP",
				"service", "tracking-service",
				"activeAmbulances", trackingCacheService.getAllLatest().size()
		));
	}
}
