package com.vivek.emergency.controller;

import jakarta.validation.Valid;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.vivek.emergency.dto.EmergencyEvent;
import com.vivek.emergency.entity.Emergency;
import com.vivek.emergency.service.EmergencyService;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/emergency")
@RequiredArgsConstructor
public class EmergencyController {
	private final EmergencyService emergencyService;
	private final MeterRegistry meterRegistry;

	@PostMapping
	public ResponseEntity<Emergency> createEmergency(@Valid @RequestBody EmergencyEvent event) {
		meterRegistry.counter("emergency.requests.total").increment();
		Emergency created = emergencyService.createEmergency(event);
		return ResponseEntity.ok(created);
	}

	@GetMapping("/{emergencyId}")
	public ResponseEntity<Emergency> getEmergency(@PathVariable String emergencyId) {
		return emergencyService.findByEmergencyId(emergencyId)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/status/{status}")
	public ResponseEntity<List<Emergency>> getEmergenciesByStatus(@PathVariable String status) {
		return ResponseEntity.ok(emergencyService.findByStatus(status));
	}

	@GetMapping("/pending")
	public ResponseEntity<List<Emergency>> getPendingEmergencies() {
		return ResponseEntity.ok(emergencyService.findPendingEmergenciesByPriority());
	}
}
