package com.vivek.dispatch.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.dispatch.dto.AmbulanceLocationEvent;
import com.vivek.dispatch.dto.AssignmentEvent;
import com.vivek.dispatch.dto.EmergencyEvent;
import com.vivek.dispatch.dto.OSRMRoute;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DispatchEngine {

	private final KafkaTemplate<String, AssignmentEvent> kafkaTemplate;
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;
	private final OSRMService osrmService;
	private final AssignmentHistoryService assignmentHistoryService;

	private final Map<String, AmbulanceLocationEvent> ambulanceState = new ConcurrentHashMap<>();
	private final Map<String, RouteEstimate> routeEstimateCache = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	private static final String ASSIGNMENT_TOPIC = "ambulance-assigned-topic";
	private static final String QUEUE_HIGH = "dispatch:queue:HIGH";
	private static final String QUEUE_MEDIUM = "dispatch:queue:MEDIUM";
	private static final String QUEUE_LOW = "dispatch:queue:LOW";
	private static final String INFLIGHT_HIGH = "dispatch:inflight:HIGH";
	private static final String INFLIGHT_MEDIUM = "dispatch:inflight:MEDIUM";
	private static final String INFLIGHT_LOW = "dispatch:inflight:LOW";
	private static final long MAX_LOCATION_AGE_MS = 60_000L;
	private static final double MAX_DISPATCH_RADIUS_KM = 20.0;
	private static final long ROUTE_ESTIMATE_CACHE_TTL_MS = 30_000L;
	private Counter emergencyQueuedCounter;
	private Counter assignmentPublishedCounter;
	private Counter noAvailableAmbulanceCounter;
	private Counter assignmentPublishFailureCounter;
	private Counter lockAcquireFailureCounter;
	private Timer assignmentPublishTimer;

	@PostConstruct
	public void startDispatcher() {
		initializeMetrics();
		scheduler.scheduleAtFixedRate(this::dispatchNextEmergency, 0, 1, TimeUnit.SECONDS);
	}

	@PreDestroy
	public void stopDispatcher() {
		scheduler.shutdown();
	}

	public void handleEmergency(EmergencyEvent emergency) {
		// Idempotency check - prevent duplicate processing on Kafka replay
		String idempotencyKey = "idempotency:emergency:" + emergency.getEmergencyId();
		Boolean isNew = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", Duration.ofMinutes(30));
		
		if (!Boolean.TRUE.equals(isNew)) {
			log.info("Duplicate emergency ignored (idempotency) emergencyId={}", emergency.getEmergencyId());
			meterRegistry.counter("dispatch.emergencies.duplicate.total").increment();
			return;
		}
		
		log.info("Emergency queued emergencyId={} priority={} lat={} lon={}", emergency.getEmergencyId(),
				emergency.getPriority(), emergency.getLat(), emergency.getLon());
		emergencyQueuedCounter.increment();
		enqueueEmergency(emergency);
	}

	public void handleAmbulanceUpdate(AmbulanceLocationEvent ambulance) {
		ambulanceState.put(ambulance.getAmbulanceId(), ambulance);
		initializeAmbulanceStateIfAbsent(ambulance.getAmbulanceId());
		log.debug("Ambulance location updated ambulanceId={} lat={} lon={} cacheSize={}", 
			ambulance.getAmbulanceId(), ambulance.getLatitude(), ambulance.getLongitude(), ambulanceState.size());
	}

	private void dispatchNextEmergency() {
		QueueClaim claim = null;
		try {
			long now = System.currentTimeMillis();
			routeEstimateCache.entrySet().removeIf(entry -> entry.getValue().expiresAtMs() <= now);

			claim = claimRawEmergency();
			if (claim == null) {
				return;
			}

			EmergencyEvent emergency = parseEmergencyPayload(claim.payload());
			if (emergency == null) {
				acknowledgeClaim(claim);
				claim = null;
				return;
			}

			log.debug("Processing emergency emergencyId={} priority={}", emergency.getEmergencyId(), emergency.getPriority());

			AmbulanceLocationEvent nearest = findNearestAvailable(emergency);
			if (nearest == null) {
				noAvailableAmbulanceCounter.increment();
				log.warn("No ambulance available emergencyId={} knownAmbulances={} availableCount={}", 
					emergency.getEmergencyId(), ambulanceState.size(), getAvailableAmbulanceCount());
				releaseClaim(claim);
				claim = null;
				return;
			}

			String ambulanceId = nearest.getAmbulanceId();
			if (!acquireLock(ambulanceId)) {
				lockAcquireFailureCounter.increment();
				log.warn("Ambulance lock acquisition failed ambulanceId={} emergencyId={}", ambulanceId,
						emergency.getEmergencyId());
				releaseClaim(claim);
				claim = null;
				return;
			}

			try {
				Timer.Sample publishTimer = Timer.start(meterRegistry);
				long assignmentVersion = getVersion(ambulanceId);

				OSRMRoute route = osrmService.getRoute(nearest.getLatitude(), nearest.getLongitude(), 
					emergency.getLat(), emergency.getLon());
				
				double distanceKm = route != null ? route.getDistance() / 1000.0 : 
					calculateDistance(emergency.getLat(), emergency.getLon(), nearest.getLatitude(), nearest.getLongitude());
				double etaSeconds = route != null ? route.getDuration() : distanceKm * 120; // fallback: ~30 km/h
				
				AssignmentEvent assignment = new AssignmentEvent(emergency.getEmergencyId(), ambulanceId, 
					distanceKm, assignmentVersion, emergency.getLat(), emergency.getLon());
				kafkaTemplate.send(ASSIGNMENT_TOPIC, emergency.getEmergencyId(), assignment).get(5, TimeUnit.SECONDS);
				
				// Don't set status here - let ambulance service handle it atomically with activeEmergencyId
				// to avoid race condition with auto-heal detecting orphan assignments
				
				acknowledgeClaim(claim);
				claim = null;
				assignmentPublishedCounter.increment();
				publishTimer.stop(assignmentPublishTimer);

				// Record assignment to PostgreSQL
				try {
					assignmentHistoryService.recordAssignment(
						emergency.getEmergencyId(),
						ambulanceId,
						distanceKm,
						(int) assignmentVersion
					);
				} catch (Exception e) {
					log.error("Failed to record assignment history emergencyId={} ambulanceId={}", 
						emergency.getEmergencyId(), ambulanceId, e);
				}

				log.info(
						"Assignment published emergencyId={} ambulanceId={} priority={} distanceKm={} etaMin={} version={}",
						emergency.getEmergencyId(), nearest.getAmbulanceId(), emergency.getPriority(), 
						String.format("%.2f", distanceKm), String.format("%.1f", etaSeconds / 60.0), assignmentVersion);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				assignmentPublishFailureCounter.increment();
				log.warn("Assignment publish interrupted emergencyId={} retry=true", emergency.getEmergencyId());
				releaseClaim(claim);
				claim = null;
			} catch (ExecutionException | TimeoutException e) {
				assignmentPublishFailureCounter.increment();
				log.error("Assignment publish failed emergencyId={} retry=true", emergency.getEmergencyId(), e);
				releaseClaim(claim);
				claim = null;
			} finally {
				releaseLock(ambulanceId);
			}
		} catch (Exception e) {
			if (claim != null) {
				releaseClaim(claim);
			}
			log.error("Dispatch loop failure", e);
		}
	}

	public void markAmbulanceAvailable(String ambulanceId, long completionVersion) {
		AmbulanceLocationEvent ambulance = ambulanceState.get(ambulanceId);
		if (ambulance != null) {
			log.info("Completion observed ambulanceId={} completionVersion={} localStatePresent=true", ambulanceId,
					completionVersion);
		} else {
			log.warn("Completion observed for unknown ambulance in local cache ambulanceId={} completionVersion={}",
					ambulanceId, completionVersion);
		}
	}

	private void initializeAmbulanceStateIfAbsent(String ambulanceId) {
		redisTemplate.opsForValue().setIfAbsent(versionKey(ambulanceId), "0");
		redisTemplate.opsForValue().setIfAbsent(statusKey(ambulanceId), "AVAILABLE");
	}

	private long getVersion(String ambulanceId) {
		String current = redisTemplate.opsForValue().get(versionKey(ambulanceId));
		return current == null ? 0L : Long.parseLong(current);
	}

	private boolean acquireLock(String ambulanceId) {
		String lockKey = lockKey(ambulanceId);
		Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(5));
		return Boolean.TRUE.equals(success);
	}

	private void releaseLock(String ambulanceId) {
		redisTemplate.delete(lockKey(ambulanceId));
	}

	private void enqueueEmergency(EmergencyEvent emergency) {
		try {
			String payload = objectMapper.writeValueAsString(emergency);
			redisTemplate.opsForList().rightPush(queueKey(emergency.getPriority()), payload);
			
			// Store a copy keyed by emergencyId — used if we need to requeue after a REJECTED ACK
			redisTemplate.opsForValue().set(
				"emergency:payload:" + emergency.getEmergencyId(),
				payload,
				Duration.ofHours(2)  // TTL: longer than any reasonable dispatch cycle
			);
		} catch (JsonProcessingException e) {
			log.error("Emergency enqueue serialization failed emergencyId={}", emergency.getEmergencyId(), e);
		}
	}

	private EmergencyEvent parseEmergencyPayload(String payload) {
		try {
			return objectMapper.readValue(payload, EmergencyEvent.class);
		} catch (JsonProcessingException e) {
			log.error("Invalid emergency payload in Redis queue. Dropping item.");
			return null;
		}
	}

	private QueueClaim claimRawEmergency() {
		String high = claimFromQueue(QUEUE_HIGH, INFLIGHT_HIGH);
		if (high != null) {
			return new QueueClaim(high, QUEUE_HIGH, INFLIGHT_HIGH);
		}

		String medium = claimFromQueue(QUEUE_MEDIUM, INFLIGHT_MEDIUM);
		if (medium != null) {
			return new QueueClaim(medium, QUEUE_MEDIUM, INFLIGHT_MEDIUM);
		}

		String low = claimFromQueue(QUEUE_LOW, INFLIGHT_LOW);
		if (low != null) {
			return new QueueClaim(low, QUEUE_LOW, INFLIGHT_LOW);
		}
		return null;
	}

	private String claimFromQueue(String sourceQueue, String inflightQueue) {
		String script = """
				local source = KEYS[1]
				local inflight = KEYS[2]
				local payload = redis.call('LPOP', source)
				if not payload then
					return nil
				end
				redis.call('RPUSH', inflight, payload)
				return payload
				""";

		DefaultRedisScript<String> redisScript = new DefaultRedisScript<>();
		redisScript.setScriptText(script);
		redisScript.setResultType(String.class);

		return redisTemplate.execute(redisScript, java.util.List.of(sourceQueue, inflightQueue));
	}

	private void acknowledgeClaim(QueueClaim claim) {
		Long removed = redisTemplate.opsForList().remove(claim.inflightQueue(), 1, claim.payload());
		if (removed == null || removed == 0) {
			log.warn("Claim acknowledgement missed payload inflightQueue={}", claim.inflightQueue());
		}
	}

	private void releaseClaim(QueueClaim claim) {
		Long removed = redisTemplate.opsForList().remove(claim.inflightQueue(), 1, claim.payload());
		if (removed != null && removed > 0) {
			redisTemplate.opsForList().leftPush(claim.sourceQueue(), claim.payload());
			return;
		}
		log.warn("Claim release skipped because payload was not found inflightQueue={} sourceQueue={}",
				claim.inflightQueue(), claim.sourceQueue());
	}

	private String queueKey(String priority) {
		String normalized = priority == null ? "LOW" : priority.trim().toUpperCase();
		return switch (normalized) {
		case "HIGH" -> QUEUE_HIGH;
		case "MEDIUM" -> QUEUE_MEDIUM;
		default -> QUEUE_LOW;
		};
	}

	private String versionKey(String ambulanceId) {
		return "ambulance:" + ambulanceId + ":version";
	}

	private String statusKey(String ambulanceId) {
		return "ambulance:" + ambulanceId + ":status";
	}

	private boolean isAvailableInRedis(String ambulanceId) {
		String rawStatus = redisTemplate.opsForValue().get(statusKey(ambulanceId));
		if (rawStatus == null || rawStatus.isBlank()) {
			log.debug("Ambulance status is null/blank, setting to AVAILABLE ambulanceId={}", ambulanceId);
			redisTemplate.opsForValue().setIfAbsent(statusKey(ambulanceId), "AVAILABLE");
			return true;
		}

		String status = rawStatus.trim().toUpperCase();
		if ("BUSY".equals(status)) {
			status = "ASSIGNED";
		}

		boolean available = switch (status) {
		case "AVAILABLE", "COMPLETED" -> true;
		case "ASSIGNED", "ON_ROUTE", "ARRIVED" -> false;
		default -> {
			log.warn("Unknown ambulance status; defaulting available ambulanceId={} rawStatus={}", ambulanceId,
					rawStatus);
			yield true;
		}
		};
		
		log.debug("Checked ambulance availability ambulanceId={} rawStatus='{}' status='{}' available={}", 
			ambulanceId, rawStatus, status, available);
		return available;
	}

	private String lockKey(String ambulanceId) {
		return "lock:ambulance:" + ambulanceId;
	}

	private AmbulanceLocationEvent findNearestAvailable(EmergencyEvent emergency) {
		AmbulanceLocationEvent nearest = null;
		double minEta = Double.MAX_VALUE;

		log.debug("Finding nearest ambulance for emergency emergencyId={} totalAmbulances={}", 
			emergency.getEmergencyId(), ambulanceState.size());

		for (AmbulanceLocationEvent ambulance : ambulanceState.values()) {
			boolean available = isAvailableInRedis(ambulance.getAmbulanceId());
			log.debug("Checking ambulance ambulanceId={} available={}", ambulance.getAmbulanceId(), available);
			
			if (!available) {
				continue;
			}
			if (!isLocationFresh(ambulance)) {
				log.debug("Skipping stale ambulance location ambulanceId={} timestamp={}",
						ambulance.getAmbulanceId(), ambulance.getTimestamp());
				continue;
			}

			double haversineDistanceKm = calculateDistance(emergency.getLat(), emergency.getLon(),
					ambulance.getLatitude(), ambulance.getLongitude());
			if (haversineDistanceKm > MAX_DISPATCH_RADIUS_KM) {
				continue;
			}

			double eta = getEtaSecondsWithCache(ambulance, emergency, haversineDistanceKm);

			if (eta < minEta) {
				minEta = eta;
				nearest = ambulance;
			}
		}

		if (nearest == null) {
			log.warn("No nearest ambulance found emergencyId={} checkedAmbulances={}", 
				emergency.getEmergencyId(), ambulanceState.size());
		} else {
			log.debug("Found nearest ambulance emergencyId={} ambulanceId={} eta={}s", 
				emergency.getEmergencyId(), nearest.getAmbulanceId(), minEta);
		}

		return nearest;
	}

	private double getEtaSecondsWithCache(
			AmbulanceLocationEvent ambulance,
			EmergencyEvent emergency,
			double fallbackDistanceKm) {
		String cacheKey = buildRouteCacheKey(ambulance, emergency);
		long now = System.currentTimeMillis();

		RouteEstimate cached = routeEstimateCache.get(cacheKey);
		if (cached != null && cached.expiresAtMs() > now) {
			return cached.etaSeconds();
		}

		OSRMRoute route = osrmService.getRoute(
				ambulance.getLatitude(),
				ambulance.getLongitude(),
				emergency.getLat(),
				emergency.getLon());
		double etaSeconds = route != null ? route.getDuration() : fallbackDistanceKm * 120;
		routeEstimateCache.put(cacheKey, new RouteEstimate(etaSeconds, now + ROUTE_ESTIMATE_CACHE_TTL_MS));
		return etaSeconds;
	}

	private String buildRouteCacheKey(AmbulanceLocationEvent ambulance, EmergencyEvent emergency) {
		return ambulance.getAmbulanceId()
				+ "|" + Math.round(ambulance.getLatitude() * 10000)
				+ "|" + Math.round(ambulance.getLongitude() * 10000)
				+ "|" + Math.round(emergency.getLat() * 10000)
				+ "|" + Math.round(emergency.getLon() * 10000);
	}

	private boolean isLocationFresh(AmbulanceLocationEvent ambulance) {
		long timestamp = ambulance.getTimestamp();
		if (timestamp <= 0) {
			return false;
		}
		return System.currentTimeMillis() - timestamp <= MAX_LOCATION_AGE_MS;
	}

	private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
		final int R = 6371;

		double latDistance = Math.toRadians(lat2 - lat1);
		double lonDistance = Math.toRadians(lon2 - lon1);

		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

		return R * c;
	}

	private void initializeMetrics() {
		emergencyQueuedCounter = meterRegistry.counter("dispatch.emergencies.queued.total");
		assignmentPublishedCounter = meterRegistry.counter("dispatch.assignments.published.total");
		noAvailableAmbulanceCounter = meterRegistry.counter("dispatch.no_available_ambulance.total");
		assignmentPublishFailureCounter = meterRegistry.counter("dispatch.assignment.publish.failures.total");
		lockAcquireFailureCounter = meterRegistry.counter("dispatch.assignment.lock.failures.total");
		assignmentPublishTimer = meterRegistry.timer("dispatch.assignment.publish.latency");

		Gauge.builder("dispatch.emergencies.queue.depth", this, DispatchEngine::getTotalQueueDepth)
				.description("Total pending emergencies across redis priority queues").register(meterRegistry);
		Gauge.builder("dispatch.ambulances.known.count", ambulanceState, Map::size)
				.description("Ambulances currently known by dispatch location cache").register(meterRegistry);
		Gauge.builder("dispatch.ambulances.available.count", this, DispatchEngine::getAvailableAmbulanceCount)
				.description("Ambulances currently available according to redis FSM").register(meterRegistry);
	}

	private double getTotalQueueDepth() {
		return safeListSize(QUEUE_HIGH) + safeListSize(QUEUE_MEDIUM) + safeListSize(QUEUE_LOW);
	}

	private double getAvailableAmbulanceCount() {
		return ambulanceState.values().stream().map(AmbulanceLocationEvent::getAmbulanceId)
				.filter(this::isAvailableInRedis).count();
	}

	private long safeListSize(String key) {
		Long size = redisTemplate.opsForList().size(key);
		return size == null ? 0L : size;
	}


	/**
	 * Called when an assignment ACK comes back REJECTED.
	 * Clears the idempotency key AND re-pushes the emergency back onto its priority queue
	 * so it gets dispatched to a different ambulance.
	 * Previously this only deleted the idempotency key, leaving the emergency orphaned.
	 */
	public void requeueEmergency(String emergencyId) {
		// 1. Remove idempotency key so re-processing is allowed
		String idempotencyKey = "idempotency:emergency:" + emergencyId;
		redisTemplate.delete(idempotencyKey);

		// 2. Also clear the ambulance status that was pre-set to ASSIGNED by dispatch
		//    (The ambulance FSM rejected the assignment, so it's still AVAILABLE in its own Redis,
		//     but the dispatch-service set ambulance:X:status=ASSIGNED optimistically — roll it back)
		String ackKey = "assignment:ack:" + emergencyId;
		redisTemplate.delete(ackKey);

		// 3. Look up the original emergency payload from the pending queue or reconstruct from DB
		//    We store a copy keyed by emergencyId at enqueue time for exactly this requeue scenario
		String payloadKey = "emergency:payload:" + emergencyId;
		String savedPayload = redisTemplate.opsForValue().get(payloadKey);

		if (savedPayload == null) {
			log.error("Cannot requeue emergencyId={} — payload not found in Redis. " +
					  "Emergency may be lost! Check PostgreSQL emergencies table for manual recovery.", emergencyId);
			meterRegistry.counter("dispatch.emergencies.requeue.lost.total").increment();
			return;
		}

		try {
			EmergencyEvent event = objectMapper.readValue(savedPayload, EmergencyEvent.class);
			enqueueEmergency(event);
			meterRegistry.counter("dispatch.emergencies.requeued.total").increment();
			log.info("Emergency re-queued after rejection emergencyId={} priority={}", emergencyId, event.getPriority());
		} catch (JsonProcessingException e) {
			log.error("Failed to deserialize saved payload for requeue emergencyId={}", emergencyId, e);
			meterRegistry.counter("dispatch.emergencies.requeue.lost.total").increment();
		}
	}

	private record QueueClaim(String payload, String sourceQueue, String inflightQueue) {
	}

	private record RouteEstimate(double etaSeconds, long expiresAtMs) {
	}

}
