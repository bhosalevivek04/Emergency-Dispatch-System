package com.vivek.emergency.controller;

import jakarta.validation.Valid;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.vivek.emergency.dto.EmergencyEvent;
import com.vivek.emergency.dto.PublicEmergencyRequest;
import com.vivek.emergency.dto.PublicEmergencyStatusResponse;
import com.vivek.emergency.entity.Emergency;
import com.vivek.emergency.service.EmergencyService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/emergency")
@RequiredArgsConstructor
public class EmergencyController {
	private final EmergencyService emergencyService;
	private final MeterRegistry meterRegistry;

	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
	public ResponseEntity<Emergency> createEmergency(@Valid @RequestBody EmergencyEvent event) {
		meterRegistry.counter("emergency.requests.total").increment();
		Emergency created = emergencyService.createEmergency(event);
		return ResponseEntity.ok(created);
	}

	@PostMapping("/public")
	public ResponseEntity<PublicEmergencyStatusResponse> createPublicEmergency(
			@Valid @RequestBody PublicEmergencyRequest request) {
		EmergencyEvent event = new EmergencyEvent();
		event.setLatitude(request.getLatitude());
		event.setLongitude(request.getLongitude());
		event.setPriority(request.getPriority());
		event.setCallerPhone(request.getCallerPhone());
		event.setDescription(request.getDescription());
		Emergency created = emergencyService.createEmergency(event);
		meterRegistry.counter("emergency.public.requests.total").increment();
		return ResponseEntity.ok(toPublicResponse(created));
	}

	@GetMapping("/public/{emergencyId}")
	public ResponseEntity<PublicEmergencyStatusResponse> getPublicEmergencyStatus(@PathVariable String emergencyId) {
		return emergencyService.findByEmergencyId(emergencyId)
				.map(e -> ResponseEntity.ok(toPublicResponse(e)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{emergencyId}/status")
	@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'AMBULANCE_DRIVER')")
	public ResponseEntity<Emergency> updateStatus(
			@PathVariable String emergencyId,
			@RequestBody Map<String, String> body) {
		String status = body.get("status");
		emergencyService.updateStatus(emergencyId, status, null);
		return emergencyService.findByEmergencyId(emergencyId)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{emergencyId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'AMBULANCE_DRIVER')")
	public ResponseEntity<Emergency> getEmergency(@PathVariable String emergencyId) {
		return emergencyService.findByEmergencyId(emergencyId)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/status/{status}")
	@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
	public ResponseEntity<List<Emergency>> getEmergenciesByStatus(@PathVariable String status) {
		return ResponseEntity.ok(emergencyService.findByStatus(status));
	}

	@GetMapping("/pending")
	@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
	public ResponseEntity<List<Emergency>> getPendingEmergencies() {
		return ResponseEntity.ok(emergencyService.findPendingEmergenciesByPriority());
	}

	private PublicEmergencyStatusResponse toPublicResponse(Emergency emergency) {
		return PublicEmergencyStatusResponse.builder()
				.emergencyId(emergency.getEmergencyId())
				.status(emergency.getStatus())
				.priority(emergency.getPriority())
				.assignedAmbulanceId(emergency.getAssignedAmbulanceId())
				.latitude(emergency.getLatitude() == null ? null : emergency.getLatitude().doubleValue())
				.longitude(emergency.getLongitude() == null ? null : emergency.getLongitude().doubleValue())
				.createdAt(emergency.getCreatedAt() == null ? null : emergency.getCreatedAt().toString())
				.updatedAt(emergency.getUpdatedAt() == null ? null : emergency.getUpdatedAt().toString())
				.build();
	}
}
