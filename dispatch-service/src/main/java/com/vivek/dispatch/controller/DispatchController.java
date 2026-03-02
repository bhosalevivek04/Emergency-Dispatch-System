package com.vivek.dispatch.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.dispatch.dto.EmergencyEvent;
import com.vivek.dispatch.dto.EmergencyRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/dispatch")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class DispatchController {

	private final KafkaTemplate<String, EmergencyEvent> emergencyKafkaTemplate;

	@PostMapping("/emergency")
	public ResponseEntity<Map<String, String>> createEmergency(@RequestBody EmergencyRequest request) {
		String emergencyId = "EMG-" + UUID.randomUUID().toString().substring(0, 8);
		
		EmergencyEvent event = new EmergencyEvent();
		event.setEmergencyId(emergencyId);
		event.setLat(request.getLat());
		event.setLon(request.getLon());
		event.setPriority(request.getPriority() != null ? request.getPriority() : "MEDIUM");
		
		emergencyKafkaTemplate.send("emergency-topic", emergencyId, event);
		
		log.info("Emergency created emergencyId={} lat={} lon={} priority={}", 
			emergencyId, request.getLat(), request.getLon(), event.getPriority());
		
		return ResponseEntity.ok(Map.of(
			"emergencyId", emergencyId,
			"status", "QUEUED",
			"message", "Emergency request received and queued for dispatch"
		));
	}

	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		return ResponseEntity.ok(Map.of(
			"status", "UP",
			"service", "dispatch-service"
		));
	}
}
