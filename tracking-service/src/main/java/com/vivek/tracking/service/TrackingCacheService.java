package com.vivek.tracking.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.tracking.dto.AmbulanceLocationEvent;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TrackingCacheService {

	private static final String TRACKED_AMBULANCES_KEY = "tracking:ambulances";
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	
	// In-memory fallback when Redis is not available
	private final Map<String, AmbulanceLocationEvent> inMemoryCache = new ConcurrentHashMap<>();

	public TrackingCacheService(
			@Autowired(required = false) StringRedisTemplate redisTemplate,
			ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		if (redisTemplate == null) {
			log.warn("Redis not available, using in-memory cache");
		}
	}

	public void saveLatest(AmbulanceLocationEvent event) {
		if (redisTemplate != null) {
			try {
				redisTemplate.opsForValue().set(locationKey(event.getAmbulanceId()), objectMapper.writeValueAsString(event));
				redisTemplate.opsForSet().add(TRACKED_AMBULANCES_KEY, event.getAmbulanceId());
			} catch (JsonProcessingException ex) {
				log.error("Unable to serialize location event ambulanceId={}", event.getAmbulanceId(), ex);
			}
		} else {
			// Use in-memory cache
			inMemoryCache.put(event.getAmbulanceId(), event);
		}
	}

	public AmbulanceLocationEvent getLatest(String ambulanceId) {
		if (redisTemplate != null) {
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
		} else {
			// Use in-memory cache
			return inMemoryCache.get(ambulanceId);
		}
	}

	public Map<String, AmbulanceLocationEvent> getAllLatest() {
		if (redisTemplate != null) {
			var ambulanceIds = redisTemplate.opsForSet().members(TRACKED_AMBULANCES_KEY);
			if (ambulanceIds == null || ambulanceIds.isEmpty()) {
				return Map.of();
			}

			Map<String, AmbulanceLocationEvent> result = new LinkedHashMap<>();
			for (String ambulanceId : ambulanceIds) {
				String payload = redisTemplate.opsForValue().get(locationKey(ambulanceId));
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
		} else {
			// Use in-memory cache
			return new LinkedHashMap<>(inMemoryCache);
		}
	}

	private String locationKey(String ambulanceId) {
		return "tracking:ambulance:" + ambulanceId + ":latest";
	}
}
