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
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	private static final String ASSIGNMENT_TOPIC = "ambulance-assigned-topic";
	private static final String QUEUE_HIGH = "dispatch:queue:HIGH";
	private static final String QUEUE_MEDIUM = "dispatch:queue:MEDIUM";
	private static final String QUEUE_LOW = "dispatch:queue:LOW";
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
		try {
			EmergencyEvent emergency = peekEmergency();
			if (emergency == null) {
				return;
			}

			log.debug("Processing emergency emergencyId={} priority={}", emergency.getEmergencyId(), emergency.getPriority());

			AmbulanceLocationEvent nearest = findNearestAvailable(emergency);
			if (nearest == null) {
				noAvailableAmbulanceCounter.increment();
				log.warn("No ambulance available emergencyId={} knownAmbulances={} availableCount={}", 
					emergency.getEmergencyId(), ambulanceState.size(), getAvailableAmbulanceCount());
				return;
			}

			String ambulanceId = nearest.getAmbulanceId();
			if (!acquireLock(ambulanceId)) {
				lockAcquireFailureCounter.increment();
				log.warn("Ambulance lock acquisition failed ambulanceId={} emergencyId={}", ambulanceId,
						emergency.getEmergencyId());
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
				
				// Immediately mark ambulance as ASSIGNED in Redis to prevent double-assignment
				redisTemplate.opsForValue().set(statusKey(ambulanceId), "ASSIGNED");
				
				acknowledgeEmergency(emergency);
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
			} catch (ExecutionException | TimeoutException e) {
				assignmentPublishFailureCounter.increment();
				log.error("Assignment publish failed emergencyId={} retry=true", emergency.getEmergencyId(), e);
			} finally {
				releaseLock(ambulanceId);
			}
		} catch (Exception e) {
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
			redisTemplate.opsForList().rightPush(queueKey(emergency.getPriority()), objectMapper.writeValueAsString(emergency));
		} catch (JsonProcessingException e) {
			log.error("Emergency enqueue serialization failed emergencyId={}", emergency.getEmergencyId(), e);
		}
	}

	private EmergencyEvent peekEmergency() {
		String payload = peekRawEmergency();
		if (payload == null) {
			return null;
		}

		try {
			return objectMapper.readValue(payload, EmergencyEvent.class);
		} catch (JsonProcessingException e) {
			log.error("Invalid emergency payload in Redis queue. Dropping item.");
			acknowledgeRawPayload(payload);
			return null;
		}
	}

	private String peekRawEmergency() {
		String high = redisTemplate.opsForList().index(QUEUE_HIGH, 0);
		if (high != null) {
			return high;
		}

		String medium = redisTemplate.opsForList().index(QUEUE_MEDIUM, 0);
		if (medium != null) {
			return medium;
		}

		return redisTemplate.opsForList().index(QUEUE_LOW, 0);
	}

	private void acknowledgeEmergency(EmergencyEvent emergency) {
		try {
			acknowledgeRawPayload(objectMapper.writeValueAsString(emergency));
		} catch (JsonProcessingException e) {
			log.error("Emergency ack serialization failed emergencyId={}", emergency.getEmergencyId(), e);
		}
	}

	private void acknowledgeRawPayload(String payload) {
		Long removedFromHigh = redisTemplate.opsForList().remove(QUEUE_HIGH, 1, payload);
		if (removedFromHigh != null && removedFromHigh > 0) {
			return;
		}

		Long removedFromMedium = redisTemplate.opsForList().remove(QUEUE_MEDIUM, 1, payload);
		if (removedFromMedium != null && removedFromMedium > 0) {
			return;
		}

		redisTemplate.opsForList().remove(QUEUE_LOW, 1, payload);
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
			redisTemplate.opsForValue().setIfAbsent(statusKey(ambulanceId), "AVAILABLE");
			return true;
		}

		String status = rawStatus.trim().toUpperCase();
		if ("BUSY".equals(status)) {
			status = "ASSIGNED";
		}

		return switch (status) {
		case "AVAILABLE", "COMPLETED" -> true;
		case "ASSIGNED", "ON_ROUTE", "ARRIVED" -> false;
		default -> {
			log.warn("Unknown ambulance status; defaulting available ambulanceId={} rawStatus={}", ambulanceId,
					rawStatus);
			yield true;
		}
		};
	}

	private String lockKey(String ambulanceId) {
		return "lock:ambulance:" + ambulanceId;
	}

	private AmbulanceLocationEvent findNearestAvailable(EmergencyEvent emergency) {
		AmbulanceLocationEvent nearest = null;
		double minEta = Double.MAX_VALUE;

		for (AmbulanceLocationEvent ambulance : ambulanceState.values()) {
			if (!isAvailableInRedis(ambulance.getAmbulanceId())) {
				continue;
			}

			OSRMRoute route = osrmService.getRoute(ambulance.getLatitude(), ambulance.getLongitude(), 
				emergency.getLat(), emergency.getLon());
			
			double eta;
			if (route != null) {
				eta = route.getDuration(); // Real road ETA in seconds
			} else {
				// Fallback to Haversine with estimated speed
				double distance = calculateDistance(emergency.getLat(), emergency.getLon(), 
					ambulance.getLatitude(), ambulance.getLongitude());
				eta = distance * 120; // ~30 km/h average speed
			}

			if (eta < minEta) {
				minEta = eta;
				nearest = ambulance;
			}
		}

		return nearest;
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


	public void requeueEmergency(String emergencyId) {
		// Remove idempotency key to allow re-processing
		String idempotencyKey = "idempotency:emergency:" + emergencyId;
		redisTemplate.delete(idempotencyKey);

		log.info("Emergency re-queued for dispatch after rejection emergencyId={}", emergencyId);
		meterRegistry.counter("dispatch.emergencies.requeued.total").increment();
	}

}
