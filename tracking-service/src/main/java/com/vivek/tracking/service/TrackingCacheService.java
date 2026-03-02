package com.vivek.tracking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.tracking.dto.AmbulanceLocationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrackingCacheService {

	private static final String TRACKED_AMBULANCES_KEY = "tracking:ambulances";
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public void saveLatest(AmbulanceLocationEvent event) {
		try {
			redisTemplate.opsForValue().set(locationKey(event.getAmbulanceId()), objectMapper.writeValueAsString(event));
			redisTemplate.opsForSet().add(TRACKED_AMBULANCES_KEY, event.getAmbulanceId());
		} catch (JsonProcessingException ex) {
			log.error("Unable to serialize location event ambulanceId={}", event.getAmbulanceId(), ex);
		}
	}

	public AmbulanceLocationEvent getLatest(String ambulanceId) {
		String payload = redisTemplate.opsForValue().get(locationKey(ambulanceId));
		if (payload == null) {
			return null;
		}
		try {
			return objectMapper.readValue(payload, AmbulanceLocationEvent.class);
		} catch (JsonProcessingException ex) {
			log.error("Unable to parse cached location ambulanceId={}", ambulanceId, ex);
			return null;
		}
	}

	public Map<String, AmbulanceLocationEvent> getAllLatest() {
		Set<String> ambulanceIds = redisTemplate.opsForSet().members(TRACKED_AMBULANCES_KEY);
		if (ambulanceIds == null || ambulanceIds.isEmpty()) {
			return Map.of();
		}

		Map<String, AmbulanceLocationEvent> result = new LinkedHashMap<>();
		List<String> keys = new ArrayList<>(ambulanceIds.size());
		for (String ambulanceId : ambulanceIds) {
			keys.add(locationKey(ambulanceId));
		}

		List<String> payloads = redisTemplate.opsForValue().multiGet(keys);
		if (payloads == null) {
			return Map.of();
		}

		int idx = 0;
		for (String ambulanceId : ambulanceIds) {
			String payload = payloads.get(idx++);
			if (payload == null) {
				continue;
			}
			try {
				result.put(ambulanceId, objectMapper.readValue(payload, AmbulanceLocationEvent.class));
			} catch (JsonProcessingException ex) {
				log.error("Unable to parse cached location ambulanceId={}", ambulanceId, ex);
			}
		}
		return result;
	}

	private String locationKey(String ambulanceId) {
		return "tracking:ambulance:" + ambulanceId + ":latest";
	}
}
