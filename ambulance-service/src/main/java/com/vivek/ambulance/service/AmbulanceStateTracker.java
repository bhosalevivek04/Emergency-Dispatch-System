package com.vivek.ambulance.service;

import jakarta.annotation.PostConstruct;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.vivek.ambulance.model.AmbulanceStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class AmbulanceStateTracker {
	@Value("${ambulance.fleet.ids:AMB-101,AMB-102,AMB-103}")
	private String fleetIdsConfig;
	
	private String[] ambulanceIds;
	
	private static final long STALE_ASSIGNMENT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);
	private static final long STALE_IN_FLIGHT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);
	private final StringRedisTemplate redisTemplate;
	private final MeterRegistry meterRegistry;

	@PostConstruct
	public void initializeAmbulances() {
		try {
			// Parse fleet IDs from configuration
			if (fleetIdsConfig == null || fleetIdsConfig.trim().isEmpty()) {
				log.error("Fleet IDs configuration is null or empty! Using default.");
				fleetIdsConfig = "AMB-101,AMB-102,AMB-103";
			}
			
			ambulanceIds = fleetIdsConfig.split(",");
			for (int i = 0; i < ambulanceIds.length; i++) {
				ambulanceIds[i] = ambulanceIds[i].trim();
			}
			
			log.info("Initializing ambulance fleet: {}", String.join(", ", ambulanceIds));
			
			for (String ambulanceId : ambulanceIds) {
				log.debug("Initializing ambulance: {}", ambulanceId);
				redisTemplate.opsForValue().setIfAbsent(statusKey(ambulanceId), AmbulanceStatus.AVAILABLE.name());
				redisTemplate.opsForValue().setIfAbsent(versionKey(ambulanceId), "0");
				redisTemplate.opsForValue().setIfAbsent(lastUpdatedKey(ambulanceId), String.valueOf(System.currentTimeMillis()));
				healIfStuck(ambulanceId);
				log.debug("Ambulance {} initialized successfully", ambulanceId);
			}
			
			log.info("Ambulance fleet initialization complete. Total ambulances: {}", ambulanceIds.length);
		} catch (Exception e) {
			log.error("Failed to initialize ambulance fleet", e);
			throw new RuntimeException("Ambulance fleet initialization failed", e);
		}
	}

	public boolean transition(String ambulanceId, AmbulanceStatus nextStatus, long expectedVersion) {
		ensureStateExists(ambulanceId);

		// Use a Lua script for true atomicity — works across multiple service instances
		String script = """
				local statusKey    = KEYS[1]
				local versionKey   = KEYS[2]
				local lastUpdKey   = KEYS[3]

				local expectedVer  = tonumber(ARGV[1])
				local nextStatus   = ARGV[2]
				local now          = ARGV[3]

				local versionRaw = redis.call('GET', versionKey)
				if not versionRaw then return -3 end
				local version = tonumber(versionRaw)
				if version ~= expectedVer then return -1 end

				local currentStatus = redis.call('GET', statusKey)
				if not currentStatus then return -3 end

				-- Validate FSM transition (simplified - full validation in Java)
				-- This Lua script focuses on atomicity, not full FSM logic

				redis.call('SET', statusKey,  nextStatus)
				redis.call('SET', versionKey, tostring(version + 1))
				redis.call('SET', lastUpdKey, now)
				return version + 1
				""";

		DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
		redisScript.setScriptText(script);
		redisScript.setResultType(Long.class);

		Long result = redisTemplate.execute(redisScript,
				java.util.List.of(statusKey(ambulanceId), versionKey(ambulanceId), lastUpdatedKey(ambulanceId)),
				String.valueOf(expectedVersion),
				nextStatus.name(),
				String.valueOf(System.currentTimeMillis()));

		if (result == null || result < 0) {
			meterRegistry.counter("ambulance.fsm.transition.failures.total",
					"reason", result == null ? "null" : (result == -1 ? "version_mismatch" : "unknown_ambulance")).increment();
			log.warn("Transition rejected ambulanceId={} to={} expectedVersion={} result={}",
					ambulanceId, nextStatus, expectedVersion, result);
			return false;
		}

		meterRegistry.counter("ambulance.fsm.transitions.total", "to_status", nextStatus.name()).increment();
		log.info("Transition applied ambulanceId={} toStatus={} newVersion={}", ambulanceId, nextStatus, result);
		return true;
	}

	public boolean assignEmergencyAtomically(String ambulanceId, String emergencyId, long expectedVersion) {
		ensureStateExists(ambulanceId);
		long now = System.currentTimeMillis();
		String script = """
				local statusKey = KEYS[1]
				local versionKey = KEYS[2]
				local lastUpdatedKey = KEYS[3]
				local activeEmergencyKey = KEYS[4]

				local expectedVersion = tonumber(ARGV[1])
				local now = ARGV[2]
				local emergencyId = ARGV[3]

				local status = redis.call('GET', statusKey)
				if not status then
					return -3
				end
				if status == 'BUSY' then
					status = 'ASSIGNED'
				end
				if status ~= 'AVAILABLE' then
					return -2
				end

				local versionRaw = redis.call('GET', versionKey)
				if not versionRaw then
					return -3
				end
				local version = tonumber(versionRaw)
				if version ~= expectedVersion then
					return -1
				end

				local newVersion = version + 1
				redis.call('SET', statusKey, 'ASSIGNED')
				redis.call('SET', versionKey, tostring(newVersion))
				redis.call('SET', lastUpdatedKey, now)
				redis.call('SET', activeEmergencyKey, emergencyId)

				return newVersion
				""";

		DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
		redisScript.setScriptText(script);
		redisScript.setResultType(Long.class);

		Long result = redisTemplate.execute(redisScript,
				java.util.List.of(statusKey(ambulanceId), versionKey(ambulanceId), lastUpdatedKey(ambulanceId),
						activeEmergencyKey(ambulanceId)),
				String.valueOf(expectedVersion), String.valueOf(now), emergencyId);

		if (result == null || result < 0) {
			meterRegistry.counter("ambulance.assignment.atomic.rejected.total").increment();
			log.warn("Atomic assignment rejected ambulanceId={} emergencyId={} expectedVersion={} resultCode={}",
					ambulanceId, emergencyId, expectedVersion, result);
			return false;
		}

		meterRegistry.counter("ambulance.assignment.atomic.applied.total").increment();
		log.info("Atomic assignment applied ambulanceId={} emergencyId={} status=ASSIGNED version={}", ambulanceId,
				emergencyId, result);
		return true;
	}

	@Scheduled(fixedRate = 60000)
	public void recoverStaleAssignments() {
		for (String ambulanceId : ambulanceIds) {
			healIfStuck(ambulanceId);
		}
	}

	public boolean isAvailable(String ambulanceId) {
		ensureStateExists(ambulanceId);
		return getStatus(ambulanceId) == AmbulanceStatus.AVAILABLE;
	}

	public AmbulanceStatus getStatus(String ambulanceId) {
		ensureStateExists(ambulanceId);
		String status = redisTemplate.opsForValue().get(statusKey(ambulanceId));
		if (status == null) {
			return null;
		}

		// Backward compatibility for old Redis values written before FSM naming cleanup.
		if ("BUSY".equalsIgnoreCase(status)) {
			redisTemplate.opsForValue().set(statusKey(ambulanceId), AmbulanceStatus.ASSIGNED.name());
			return AmbulanceStatus.ASSIGNED;
		}

		return AmbulanceStatus.valueOf(status);
	}

	public long getVersion(String ambulanceId) {
		ensureStateExists(ambulanceId);
		String version = redisTemplate.opsForValue().get(versionKey(ambulanceId));
		return version == null ? -1 : Long.parseLong(version);
	}

	private String versionKey(String ambulanceId) {
		return "ambulance:" + ambulanceId + ":version";
	}

	private String statusKey(String ambulanceId) {
		return "ambulance:" + ambulanceId + ":status";
	}

	private String activeEmergencyKey(String ambulanceId) {
		return "ambulance:" + ambulanceId + ":activeEmergencyId";
	}

	private String lastUpdatedKey(String ambulanceId) {
		return "ambulance:" + ambulanceId + ":lastUpdated";
	}

	private void ensureStateExists(String ambulanceId) {
		redisTemplate.opsForValue().setIfAbsent(statusKey(ambulanceId), AmbulanceStatus.AVAILABLE.name());
		redisTemplate.opsForValue().setIfAbsent(versionKey(ambulanceId), "0");
		redisTemplate.opsForValue().setIfAbsent(lastUpdatedKey(ambulanceId), String.valueOf(System.currentTimeMillis()));
	}

	public void healIfStuck(String ambulanceId) {
		ensureStateExists(ambulanceId);
		long now = System.currentTimeMillis();

		String script = """
				local statusKey = KEYS[1]
				local versionKey = KEYS[2]
				local lastUpdatedKey = KEYS[3]
				local activeEmergencyKey = KEYS[4]

				local now = tonumber(ARGV[1])
				local staleAssignmentTimeoutMs = tonumber(ARGV[2])
				local staleInFlightTimeoutMs = tonumber(ARGV[3])

				local status = redis.call('GET', statusKey)
				if not status then
					return -3
				end
				if status == 'BUSY' then
					status = 'ASSIGNED'
					redis.call('SET', statusKey, 'ASSIGNED')
				end
				if status == 'AVAILABLE' or status == 'COMPLETED' then
					return 0
				end

				local lastUpdatedRaw = redis.call('GET', lastUpdatedKey)
				local lastUpdated = tonumber(lastUpdatedRaw or '0')
				local ageMs = now - lastUpdated

				local activeEmergencyId = redis.call('GET', activeEmergencyKey)
				local isOrphanAssigned = status == 'ASSIGNED' and (not activeEmergencyId or activeEmergencyId == '')
				local isStaleAssigned = status == 'ASSIGNED' and ageMs > staleAssignmentTimeoutMs
				local isStaleInFlight = (status == 'ON_ROUTE' or status == 'ARRIVED') and ageMs > staleInFlightTimeoutMs

				if not isOrphanAssigned and not isStaleAssigned and not isStaleInFlight then
					return 0
				end

				local versionRaw = redis.call('GET', versionKey)
				local version = tonumber(versionRaw or '0')
				redis.call('SET', statusKey, 'AVAILABLE')
				redis.call('SET', versionKey, tostring(version + 1))
				redis.call('SET', lastUpdatedKey, tostring(now))
				redis.call('DEL', activeEmergencyKey)

				if isOrphanAssigned then return 1 end
				if isStaleAssigned then return 2 end
				return 3
				""";

		DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
		redisScript.setScriptText(script);
		redisScript.setResultType(Long.class);

		Long result = redisTemplate.execute(redisScript,
				java.util.List.of(statusKey(ambulanceId), versionKey(ambulanceId), lastUpdatedKey(ambulanceId),
						activeEmergencyKey(ambulanceId)),
				String.valueOf(now), String.valueOf(STALE_ASSIGNMENT_TIMEOUT_MS), String.valueOf(STALE_IN_FLIGHT_TIMEOUT_MS));

		if (result == null || result <= 0) {
			return;
		}

		String reason = switch (result.intValue()) {
		case 1 -> "ORPHAN_ASSIGNED";
		case 2 -> "STALE_ASSIGNED";
		case 3 -> "STALE_IN_FLIGHT";
		default -> "UNKNOWN";
		};
		long newVersion = getVersion(ambulanceId);
		meterRegistry.counter("ambulance.auto_heal.total", "reason", reason).increment();
		log.warn("Auto-heal applied ambulanceId={} reason={} newVersion={} newStatus=AVAILABLE",
				ambulanceId, reason, newVersion);
	}
}
