package com.vivek.ambulance.listener;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.ambulance.dto.AssignmentEvent;
import com.vivek.ambulance.dto.CompletionEvent;
import com.vivek.ambulance.model.AmbulanceStatus;
import com.vivek.ambulance.service.AmbulanceProducer;
import com.vivek.ambulance.service.AmbulanceStateTracker;
import com.vivek.ambulance.service.AmbulanceMovementSimulator;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AmbulanceAssignmentListener {
	private static final int TRANSITION_RETRY_INTERVAL_SECONDS = 2;
	private static final int MAX_TRANSITION_RETRIES = 120;
	private static final int MOVEMENT_CHECK_INTERVAL_SECONDS = 20;
	private static final double STAGNANT_DISTANCE_THRESHOLD_METERS = 15.0;
	private static final int MAX_STAGNANT_CHECKS_BEFORE_REROUTE = 2;

	private final ObjectMapper objectMapper;
	private final AmbulanceProducer ambulanceProducer;
	private final AmbulanceStateTracker ambulanceStateTracker;
	private final AmbulanceMovementSimulator movementSimulator;
	private final StringRedisTemplate redisTemplate;
	private final MeterRegistry meterRegistry;
	private ScheduledExecutorService scheduler;

	@Value("${ambulance.fleet.ids:AMB-101,AMB-102,AMB-103}")
	private String fleetIdsConfig;

	public AmbulanceAssignmentListener(
			ObjectMapper objectMapper,
			AmbulanceProducer ambulanceProducer,
			AmbulanceStateTracker ambulanceStateTracker,
			AmbulanceMovementSimulator movementSimulator,
			StringRedisTemplate redisTemplate,
			MeterRegistry meterRegistry) {
		this.objectMapper = objectMapper;
		this.ambulanceProducer = ambulanceProducer;
		this.ambulanceStateTracker = ambulanceStateTracker;
		this.movementSimulator = movementSimulator;
		this.redisTemplate = redisTemplate;
		this.meterRegistry = meterRegistry;
	}

	@PostConstruct
	public void initializeScheduler() {
		int fleetCount = 0;
		if (fleetIdsConfig != null && !fleetIdsConfig.isBlank()) {
			fleetCount = (int) java.util.Arrays.stream(fleetIdsConfig.split(","))
					.map(String::trim)
					.filter(id -> !id.isBlank())
					.count();
		}
		int poolSize = Math.max(4, fleetCount * 2);
		scheduler = Executors.newScheduledThreadPool(poolSize);
		log.info("Initialized assignment scheduler thread pool size={} fleetCount={}", poolSize, fleetCount);
	}

	@KafkaListener(topics = "ambulance-assigned-topic", groupId = "ambulance-driver-group")
	public void consumeAssignment(String message) {
		// Never propagate JsonProcessingException — it causes infinite Kafka retry loops
		AssignmentEvent assignment;
		try {
			assignment = objectMapper.readValue(message, AssignmentEvent.class);
		} catch (JsonProcessingException e) {
			meterRegistry.counter("ambulance.assignments.parse_error.total").increment();
			log.error("Malformed assignment message — discarding: {}", message, e);
			return;
		}

		meterRegistry.counter("ambulance.assignments.consumed.total").increment();
		String ambulanceId = assignment.getAmbulanceId();
		String emergencyId = assignment.getEmergencyId();
		String assignmentKey = assignment.getEmergencyId() + "-" + ambulanceId;

		if (isDuplicate("assignment", assignmentKey)) {
			meterRegistry.counter("ambulance.assignments.duplicate.total").increment();
			log.info("Duplicate assignment ignored assignmentKey={}", assignmentKey);
			return;
		}

		AmbulanceStatus currentStatus = ambulanceStateTracker.getStatus(ambulanceId);
		long currentVersion = ambulanceStateTracker.getVersion(ambulanceId);
		String activeEmergencyId = redisTemplate.opsForValue().get(activeEmergencyKey(ambulanceId));
		if (currentStatus == AmbulanceStatus.ASSIGNED && emergencyId.equals(activeEmergencyId)) {
			log.info(
					"Duplicate assignment ignored for same incident ambulanceId={} emergencyId={} currentVersion={}",
					ambulanceId, emergencyId, currentVersion);
			return;
		}

		long assignedExpectedVersion = assignment.getVersion();
		boolean assigned = ambulanceStateTracker.assignEmergencyAtomically(ambulanceId, emergencyId,
				assignedExpectedVersion);
		if (!assigned) {
			meterRegistry.counter("ambulance.assignments.rejected.total").increment();
			log.warn(
					"Assignment rejected by FSM assignmentKey={} ambulanceId={} emergencyId={} currentStatus={} currentVersion={} incomingVersion={}",
					assignmentKey, ambulanceId, emergencyId, currentStatus, currentVersion, assignedExpectedVersion);
			
			// Send REJECTED ACK to dispatch service
			sendAssignmentAck(emergencyId, ambulanceId, "REJECTED", assignedExpectedVersion);
			return;
		}
		
		// Read the ACTUAL version from Redis after atomic assignment — don't assume +1
		long versionAfterAssign = ambulanceStateTracker.getVersion(ambulanceId);
		sendAssignmentAck(emergencyId, ambulanceId, "ACCEPTED", versionAfterAssign);

		// Set destination for movement simulator from assignment event
		double estimatedDurationSeconds = 0;
		if (assignment.getEmergencyLat() != 0 && assignment.getEmergencyLon() != 0) {
			estimatedDurationSeconds = movementSimulator.setDestination(ambulanceId, assignment.getEmergencyLat(), assignment.getEmergencyLon());
			log.info("Ambulance {} will move to emergency at ({}, {}), ETA: {} minutes", 
				ambulanceId, assignment.getEmergencyLat(), assignment.getEmergencyLon(), estimatedDurationSeconds / 60.0);
		}
		
		// Calculate dynamic state transition times based on actual route duration
		// If no duration available (OSRM failed), use default timings
		long onRouteDelay, arrivedDelay, completedDelay;
		
		if (estimatedDurationSeconds > 0) {
			// ON_ROUTE: 10% of journey time (ambulance starts moving)
			onRouteDelay = Math.max(2, (long)(estimatedDurationSeconds * 0.1));
			
			// ARRIVED: 90% of journey time (ambulance reaches destination)
			arrivedDelay = Math.max(5, (long)(estimatedDurationSeconds * 0.9));
			
			// COMPLETED: Full journey time + 30 seconds for patient loading
			completedDelay = (long)(estimatedDurationSeconds + 30);
			
			log.info("Dynamic state transitions for {}: ON_ROUTE in {}s, ARRIVED in {}s, COMPLETED in {}s",
				ambulanceId, onRouteDelay, arrivedDelay, completedDelay);
		} else {
			// Fallback to default timings - use longer delays for demo visibility
			// Get ambulance current location from movement simulator
			var currentLocation = movementSimulator.getCurrentLocation(ambulanceId);
			if (currentLocation != null && assignment.getEmergencyLat() != 0 && assignment.getEmergencyLon() != 0) {
				double straightLineDistance = calculateStraightLineDistance(
					currentLocation.lat, currentLocation.lon,
					assignment.getEmergencyLat(), assignment.getEmergencyLon()
				);
				
				// Assume 30 km/h average speed for estimation
				double estimatedMinutes = (straightLineDistance / 30.0) * 60.0;
				long estimatedSeconds = Math.max(60, (long)(estimatedMinutes * 60));
				
				onRouteDelay = Math.max(5, (long)(estimatedSeconds * 0.1));
				arrivedDelay = Math.max(30, (long)(estimatedSeconds * 0.9));
				completedDelay = estimatedSeconds + 30;
				
				log.info("Using fallback state transitions for {} (no OSRM route): distance={}km, ON_ROUTE in {}s, ARRIVED in {}s, COMPLETED in {}s",
					ambulanceId, String.format("%.2f", straightLineDistance), onRouteDelay, arrivedDelay, completedDelay);
			} else {
				// Ultimate fallback - use minimum delays
				onRouteDelay = 5;
				arrivedDelay = 60;
				completedDelay = 120;
				log.info("Using minimum fallback state transitions for {} (no location data)", ambulanceId);
			}
		}

		// FIX: Read version fresh from Redis inside each lambda instead of pre-computing offsets.
		// Pre-computing (version+1, version+2, version+3) breaks if any external event changes
		// the version before the scheduled task fires (auto-heal, race condition, etc.)
		scheduler.schedule(() -> safeTransition(ambulanceId, AmbulanceStatus.ON_ROUTE, emergencyId), onRouteDelay, TimeUnit.SECONDS);
		scheduler.schedule(() -> transitionArrivedWhenReached(ambulanceId, emergencyId, 0), arrivedDelay, TimeUnit.SECONDS);
		scheduler.schedule(() -> completeTrip(assignment, emergencyId, 0), completedDelay, TimeUnit.SECONDS);
		scheduler.schedule(
				() -> monitorMovementAndRecalculateRoute(assignment, emergencyId, Double.NaN, Double.NaN, 0),
				MOVEMENT_CHECK_INTERVAL_SECONDS,
				TimeUnit.SECONDS);
	}

	private void monitorMovementAndRecalculateRoute(
			AssignmentEvent assignment,
			String emergencyId,
			double previousLat,
			double previousLon,
			int stagnantChecks) {
		String ambulanceId = assignment.getAmbulanceId();

		String activeEmergencyId = redisTemplate.opsForValue().get(activeEmergencyKey(ambulanceId));
		if (!emergencyId.equals(activeEmergencyId)) {
			// Assignment no longer active for this ambulance; stop monitoring.
			return;
		}

		AmbulanceStatus status = ambulanceStateTracker.getStatus(ambulanceId);
		if (status != AmbulanceStatus.ASSIGNED && status != AmbulanceStatus.ON_ROUTE) {
			return;
		}

		if (movementSimulator.hasReachedDestination(ambulanceId)) {
			return;
		}

		var current = movementSimulator.getCurrentLocation(ambulanceId);
		if (current == null) {
			scheduleNextMovementCheck(assignment, emergencyId, previousLat, previousLon, stagnantChecks);
			return;
		}

		int nextStagnantChecks = stagnantChecks;
		if (!Double.isNaN(previousLat) && !Double.isNaN(previousLon)) {
			double movedMeters = haversineMeters(previousLat, previousLon, current.lat, current.lon);
			if (movedMeters < STAGNANT_DISTANCE_THRESHOLD_METERS) {
				nextStagnantChecks++;
				log.warn("Ambulance appears stalled ambulanceId={} emergencyId={} movedMeters={} stagnantChecks={}",
						ambulanceId, emergencyId, String.format("%.2f", movedMeters), nextStagnantChecks);
			} else {
				nextStagnantChecks = 0;
			}
		}

		if (nextStagnantChecks >= MAX_STAGNANT_CHECKS_BEFORE_REROUTE) {
			double newEta = movementSimulator.setDestination(
					ambulanceId,
					assignment.getEmergencyLat(),
					assignment.getEmergencyLon());
			meterRegistry.counter("ambulance.movement.reroute.total").increment();
			log.warn("Recalculated route for stalled ambulance ambulanceId={} emergencyId={} newEtaSeconds={}",
					ambulanceId, emergencyId, String.format("%.1f", newEta));

			// If ambulance remained ASSIGNED, push it to ON_ROUTE after reroute.
			if (status == AmbulanceStatus.ASSIGNED) {
				safeTransition(ambulanceId, AmbulanceStatus.ON_ROUTE, emergencyId);
			}
			nextStagnantChecks = 0;
		}

		scheduleNextMovementCheck(assignment, emergencyId, current.lat, current.lon, nextStagnantChecks);
	}

	private void scheduleNextMovementCheck(
			AssignmentEvent assignment,
			String emergencyId,
			double lat,
			double lon,
			int stagnantChecks) {
		scheduler.schedule(
				() -> monitorMovementAndRecalculateRoute(assignment, emergencyId, lat, lon, stagnantChecks),
				MOVEMENT_CHECK_INTERVAL_SECONDS,
				TimeUnit.SECONDS);
	}

	private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
		final double R = 6371000.0;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLon / 2) * Math.sin(dLon / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return R * c;
	}

	/**
	 * Reads current version from Redis at execution time — safe against version drift
	 * from auto-heal or other concurrent events between scheduling and execution.
	 */
	private void safeTransition(String ambulanceId, AmbulanceStatus targetStatus, String emergencyId) {
		long currentVersion = ambulanceStateTracker.getVersion(ambulanceId);
		AmbulanceStatus currentStatus = ambulanceStateTracker.getStatus(ambulanceId);

		// Guard: only transition if ambulance is still serving this emergency
		String activeEmergencyId = redisTemplate.opsForValue().get(activeEmergencyKey(ambulanceId));
		if (!emergencyId.equals(activeEmergencyId)) {
			log.warn("Skipping {} transition for {} — activeEmergency mismatch (expected={} actual={})",
					targetStatus, ambulanceId, emergencyId, activeEmergencyId);
			return;
		}

		boolean ok = ambulanceStateTracker.transition(ambulanceId, targetStatus, currentVersion);
		if (!ok) {
			log.warn("Transition to {} failed for {} at version {} (currentStatus={}). " +
							"Auto-heal will recover if stuck.",
					targetStatus, ambulanceId, currentVersion, currentStatus);
		}
	}

	private void transitionArrivedWhenReached(String ambulanceId, String emergencyId, int retryCount) {
		String activeEmergencyId = redisTemplate.opsForValue().get(activeEmergencyKey(ambulanceId));
		if (!emergencyId.equals(activeEmergencyId)) {
			log.warn("Skipping ARRIVED transition for {} — activeEmergency mismatch (expected={} actual={})",
					ambulanceId, emergencyId, activeEmergencyId);
			return;
		}

		if (!movementSimulator.hasReachedDestination(ambulanceId)) {
			if (retryCount >= MAX_TRANSITION_RETRIES) {
				log.warn("ARRIVED transition forced for {} after {} retries; destination not confirmed",
						ambulanceId, retryCount);
				safeTransition(ambulanceId, AmbulanceStatus.ARRIVED, emergencyId);
				return;
			}
			scheduler.schedule(
					() -> transitionArrivedWhenReached(ambulanceId, emergencyId, retryCount + 1),
					TRANSITION_RETRY_INTERVAL_SECONDS,
					TimeUnit.SECONDS);
			return;
		}

		safeTransition(ambulanceId, AmbulanceStatus.ARRIVED, emergencyId);
	}

	private void completeTrip(AssignmentEvent assignment, String emergencyId, int retryCount) {
		String ambulanceId = assignment.getAmbulanceId();

		String activeEmergencyId = redisTemplate.opsForValue().get(activeEmergencyKey(ambulanceId));
		if (!emergencyId.equals(activeEmergencyId)) {
			log.warn("Skipping COMPLETED transition for {} — activeEmergency mismatch", ambulanceId);
			return;
		}

		if (!movementSimulator.hasReachedDestination(ambulanceId)
				|| ambulanceStateTracker.getStatus(ambulanceId) != AmbulanceStatus.ARRIVED) {
			if (retryCount >= MAX_TRANSITION_RETRIES) {
				log.warn("COMPLETED transition forced for {} after {} retries; destination/status not ready",
						ambulanceId, retryCount);
			} else {
				scheduler.schedule(
						() -> completeTrip(assignment, emergencyId, retryCount + 1),
						TRANSITION_RETRY_INTERVAL_SECONDS,
						TimeUnit.SECONDS);
				return;
			}
		}

		long currentVersion = ambulanceStateTracker.getVersion(ambulanceId);
		boolean completed = ambulanceStateTracker.transition(ambulanceId, AmbulanceStatus.COMPLETED, currentVersion);
		if (!completed) {
			log.warn("COMPLETED transition failed for {} — auto-heal will recover", ambulanceId);
			return;
		}

		long completionVersion = ambulanceStateTracker.getVersion(ambulanceId);
		CompletionEvent completion = new CompletionEvent(ambulanceId, assignment.getEmergencyId(),
				"COMPLETED", completionVersion);
		ambulanceProducer.sendCompletion(completion);
		meterRegistry.counter("ambulance.completions.published.total").increment();
		redisTemplate.delete(activeEmergencyKey(ambulanceId));
		movementSimulator.clearDestination(ambulanceId);

		// Immediately transition back to AVAILABLE
		boolean available = ambulanceStateTracker.transition(ambulanceId, AmbulanceStatus.AVAILABLE, completionVersion);
		if (!available) {
			log.warn("AVAILABLE transition failed for {} after completion — auto-heal will recover", ambulanceId);
		}

		log.info("Trip completed ambulanceId={} emergencyId={}", ambulanceId, assignment.getEmergencyId());
	}

	@PreDestroy
	public void shutdown() {
		if (scheduler != null) {
			scheduler.shutdown();
		}
	}

	private boolean isDuplicate(String type, String key) {
		String redisKey = "idempotency:" + type + ":" + key;
		Boolean created = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", Duration.ofMinutes(10));
		return !Boolean.TRUE.equals(created);
	}

	private String activeEmergencyKey(String ambulanceId) {
		return "ambulance:" + ambulanceId + ":activeEmergencyId";
	}
	
	private double calculateStraightLineDistance(double lat1, double lon1, double lat2, double lon2) {
		final int R = 6371; // Earth radius in km
		double latDistance = Math.toRadians(lat2 - lat1);
		double lonDistance = Math.toRadians(lon2 - lon1);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return R * c;
	}
	
	private void sendAssignmentAck(String emergencyId, String ambulanceId, String status, long version) {
		try {
			ambulanceProducer.sendAcknowledgment("ambulance-assignment-ack-topic", emergencyId, ambulanceId, status, version);
			log.info("Assignment ACK sent emergencyId={} ambulanceId={} status={}", emergencyId, ambulanceId, status);
		} catch (Exception e) {
			log.error("Failed to send assignment ACK emergencyId={} ambulanceId={}", emergencyId, ambulanceId, e);
		}
	}
}
