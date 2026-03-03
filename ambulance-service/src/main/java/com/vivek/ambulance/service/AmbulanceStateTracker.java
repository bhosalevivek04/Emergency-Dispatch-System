package com.vivek.ambulance.service;

import jakarta.annotation.PostConstruct;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.vivek.ambulance.model.AmbulanceState;
import com.vivek.ambulance.model.AmbulanceStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class AmbulanceStateTracker {
	private static final String[] AMBULANCES = { "AMB-101", "AMB-102", "AMB-103" };
	private static final long STALE_ASSIGNMENT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);
	private static final long STALE_IN_FLIGHT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);
	private final StringRedisTemplate redisTemplate;
	private final MeterRegistry meterRegistry;

	@PostConstruct
	public void initializeAmbulances() {
		for (String ambulanceId : AMBULANCES) {
			redisTemplate.opsForValue().setIfAbsent(statusKey(ambulanceId), AmbulanceStatus.AVAILABLE.name());
			redisTemplate.opsForValue().setIfAbsent(versionKey(ambulanceId), "0");
			redisTemplate.opsForValue().setIfAbsent(lastUpdatedKey(ambulanceId), String.valueOf(System.currentTimeMillis()));
			healIfStuck(ambulanceId);
		}
	}

	public synchronized boolean transition(String ambulanceId, AmbulanceStatus nextStatus, long expectedVersion) {
		ensureStateExists(ambulanceId);
		AmbulanceState state = getState(ambulanceId);
		if (state == null) {
			meterRegistry.counter("ambulance.fsm.transition.failures.total", "reason", "unknown_ambulance").increment();
			log.warn("Transition rejected unknown ambulance ambulanceId={} nextStatus={} expectedVersion={}", ambulanceId,
					nextStatus, expectedVersion);
			return false;
		}

		if (state.getVersion() != expectedVersion) {
			meterRegistry.counter("ambulance.fsm.transition.failures.total", "reason", "version_mismatch").increment();
			log.warn("Transition rejected version mismatch ambulanceId={} expectedVersion={} currentVersion={}",
					ambulanceId, expectedVersion, state.getVersion());
			return false;
		}

		if (!isValidTransition(state.getStatus(), nextStatus)) {
			meterRegistry.counter("ambulance.fsm.transition.failures.total", "reason", "invalid_transition").increment();
			log.warn("Transition rejected invalid transition ambulanceId={} currentStatus={} nextStatus={}", ambulanceId,
					state.getStatus(), nextStatus);
			return false;
		}

		long newVersion = state.getVersion() + 1;
		redisTemplate.opsForValue().set(statusKey(ambulanceId), nextStatus.name());
		redisTemplate.opsForValue().set(versionKey(ambulanceId), String.valueOf(newVersion));
		redisTemplate.opsForValue().set(lastUpdatedKey(ambulanceId), String.valueOf(System.currentTimeMillis()));
		meterRegistry.counter("ambulance.fsm.transitions.total", "to_status", nextStatus.name()).increment();
		log.info("Transition applied ambulanceId={} fromStatus={} toStatus={} version={}", ambulanceId,
				state.getStatus(), nextStatus, newVersion);
		return true;
	}

	public synchronized boolean assignEmergencyAtomically(String ambulanceId, String emergencyId, long expectedVersion) {
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
		for (String ambulanceId : AMBULANCES) {
			healIfStuck(ambulanceId);
		}
	}

	private boolean isValidTransition(AmbulanceStatus current, AmbulanceStatus next) {
		if (current == null) {
			return false;
		}

		return switch (current) {
		case AVAILABLE -> next == AmbulanceStatus.ASSIGNED;
		case ASSIGNED -> next == AmbulanceStatus.ON_ROUTE;
		case ON_ROUTE -> next == AmbulanceStatus.ARRIVED;
		case ARRIVED -> next == AmbulanceStatus.COMPLETED;
		case COMPLETED -> next == AmbulanceStatus.AVAILABLE;
		};
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

	private AmbulanceState getState(String ambulanceId) {
		AmbulanceStatus status = getStatus(ambulanceId);
		long version = getVersion(ambulanceId);
		if (status == null || version < 0) {
			return null;
		}
		return new AmbulanceState(status, version);
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

	private void healIfStuck(String ambulanceId) {
		AmbulanceStatus status = getStatus(ambulanceId);
		if (status == null || status == AmbulanceStatus.AVAILABLE || status == AmbulanceStatus.COMPLETED) {
			return;
		}

		long now = System.currentTimeMillis();
		long lastUpdated = parseLongOrDefault(redisTemplate.opsForValue().get(lastUpdatedKey(ambulanceId)), 0L);
		long ageMs = now - lastUpdated;
		String activeEmergencyId = redisTemplate.opsForValue().get(activeEmergencyKey(ambulanceId));
		boolean isOrphanAssigned = status == AmbulanceStatus.ASSIGNED
				&& (activeEmergencyId == null || activeEmergencyId.isBlank());
		boolean isStaleAssigned = status == AmbulanceStatus.ASSIGNED && ageMs > STALE_ASSIGNMENT_TIMEOUT_MS;
		boolean isStaleInFlight = (status == AmbulanceStatus.ON_ROUTE || status == AmbulanceStatus.ARRIVED)
				&& ageMs > STALE_IN_FLIGHT_TIMEOUT_MS;

		if (!isOrphanAssigned && !isStaleAssigned && !isStaleInFlight) {
			return;
		}

		long currentVersion = getVersion(ambulanceId);
		redisTemplate.opsForValue().set(statusKey(ambulanceId), AmbulanceStatus.AVAILABLE.name());
		redisTemplate.opsForValue().set(versionKey(ambulanceId), String.valueOf(currentVersion + 1));
		redisTemplate.opsForValue().set(lastUpdatedKey(ambulanceId), String.valueOf(now));
		redisTemplate.delete(activeEmergencyKey(ambulanceId));
		String reason = isOrphanAssigned ? "ORPHAN_ASSIGNED"
				: isStaleAssigned ? "STALE_ASSIGNED"
						: "STALE_" + status.name();
		meterRegistry.counter("ambulance.auto_heal.total", "reason", reason).increment();
		log.warn(
				"Auto-heal applied ambulanceId={} reason={} ageMs={} previousVersion={} newVersion={} newStatus=AVAILABLE",
				ambulanceId, reason, ageMs, currentVersion, currentVersion + 1);
	}

	private long parseLongOrDefault(String value, long defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ex) {
			return defaultValue;
		}
	}
}
