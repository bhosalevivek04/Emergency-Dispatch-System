package com.vivek.dispatch.controller;

import java.util.HashMap;
import java.util.Map;

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
		
		// Check Redis directly
		String amb101Status = redisTemplate.opsForValue().get("ambulance:AMB-101:status");
		String amb102Status = redisTemplate.opsForValue().get("ambulance:AMB-102:status");
		String amb103Status = redisTemplate.opsForValue().get("ambulance:AMB-103:status");
		
		Map<String, String> redisStatus = new HashMap<>();
		redisStatus.put("AMB-101", amb101Status);
		redisStatus.put("AMB-102", amb102Status);
		redisStatus.put("AMB-103", amb103Status);
		result.put("redisStatus", redisStatus);
		
		// Check what dispatch engine sees
		Map<String, Boolean> dispatchView = new HashMap<>();
		dispatchView.put("AMB-101", isAvailableViaReflection("AMB-101"));
		dispatchView.put("AMB-102", isAvailableViaReflection("AMB-102"));
		dispatchView.put("AMB-103", isAvailableViaReflection("AMB-103"));
		result.put("dispatchView", dispatchView);
		
		return result;
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
