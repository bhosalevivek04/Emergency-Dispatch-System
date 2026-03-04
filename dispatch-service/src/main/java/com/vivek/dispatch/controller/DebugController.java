package com.vivek.dispatch.controller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.dispatch.service.DispatchEngine;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugController {
	
	private final StringRedisTemplate redisTemplate;
	private final DispatchEngine dispatchEngine;
	
	@GetMapping("/ambulance-status")
	public Map<String, Object> getAmbulanceStatus() {
		Map<String, Object> result = new HashMap<>();
		
		// Discover all known ambulance IDs from Redis keys.
		Set<String> ambulanceIds = discoverAmbulanceIds();
		
		Map<String, String> redisStatus = new HashMap<>();
		Map<String, Boolean> dispatchView = new HashMap<>();
		for (String ambulanceId : ambulanceIds) {
			redisStatus.put(ambulanceId, redisTemplate.opsForValue().get("ambulance:" + ambulanceId + ":status"));
			dispatchView.put(ambulanceId, isAvailableViaReflection(ambulanceId));
		}

		if (redisStatus.isEmpty()) {
			redisStatus.put("info", "No ambulance status keys found");
		}
		result.put("redisStatus", redisStatus);
		
		result.put("dispatchView", dispatchView);
		
		return result;
	}

	private Set<String> discoverAmbulanceIds() {
		Set<String> ids = new HashSet<>();
		Set<String> statusKeys = redisTemplate.keys("ambulance:*:status");
		if (statusKeys == null) {
			return ids;
		}
		for (String key : statusKeys) {
			// key format: ambulance:<ID>:status
			String[] parts = key.split(":");
			if (parts.length >= 3 && parts[1] != null && !parts[1].isBlank()) {
				ids.add(parts[1]);
			}
		}
		return ids;
	}
	
	private boolean isAvailableViaReflection(String ambulanceId) {
		try {
			String rawStatus = redisTemplate.opsForValue().get("ambulance:" + ambulanceId + ":status");
			if (rawStatus == null || rawStatus.isBlank()) {
				return true;
			}
			String status = rawStatus.trim().toUpperCase();
			if ("BUSY".equals(status)) {
				status = "ASSIGNED";
			}
			return switch (status) {
				case "AVAILABLE", "COMPLETED" -> true;
				case "ASSIGNED", "ON_ROUTE", "ARRIVED" -> false;
				default -> true;
			};
		} catch (Exception e) {
			return false;
		}
	}
}
