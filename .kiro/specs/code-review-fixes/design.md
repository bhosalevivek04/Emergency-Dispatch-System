# Bugfix Design Document

## Overview

This design document outlines the technical approach to fix 22 code review issues in the Emergency Dispatch System. Fixes are organized by priority: Critical (5), Serious Architectural (6), Design & Code Quality (7), and Minor (4).

## Design Principles

1. **Atomicity**: Use Transactional Outbox pattern and Redis Lua scripts for distributed atomicity
2. **Resilience**: Catch exceptions at boundaries, implement retry with backoff, avoid silent failures
3. **Security**: Externalize all credentials, restrict endpoint exposure, use environment-specific configs
4. **Maintainability**: Consolidate duplicated code, add comprehensive tests, use declarative patterns
5. **Performance**: Replace recursion with iteration, optimize initialization, cache appropriately

## Critical Fixes (Priority 1)

### Fix 1.1: Implement Transactional Outbox Pattern

**Problem**: Dual-write to Postgres + Kafka can fail partially, causing data loss

**Solution**: 
- Create `OutboxEvent` entity with fields: id, aggregateType, aggregateId, kafkaTopic, payload, published, publishedAt, retryCount, errorMessage, createdAt
- Create `OutboxEventRepository` with queries: findUnpublishedEvents(), countUnpublished(), deleteOldPublishedEvents()
- Create `OutboxPublisher` service with @Scheduled poller (1s interval) that publishes pending events to Kafka
- Modify `EmergencyService.createEmergency()` to write emergency + outbox entry in single @Transactional method
- Add database migration for outbox_events table with index on (published, createdAt)

**Files**:
- NEW: `emergency-service/src/main/java/com/vivek/emergency/entity/OutboxEvent.java`
- NEW: `emergency-service/src/main/java/com/vivek/emergency/repository/OutboxEventRepository.java`
- NEW: `emergency-service/src/main/java/com/vivek/emergency/service/OutboxPublisher.java`
- MODIFY: `emergency-service/src/main/java/com/vivek/emergency/service/EmergencyService.java`
- NEW: `emergency-service/src/main/resources/db/migration/V2__create_outbox_events.sql`

**Validation**:
- Emergency saved to DB but Kafka down → outbox entry created, published when Kafka recovers
- Emergency + outbox write fails → both rolled back, no orphaned data
- Outbox publisher retries up to 5 times with exponential backoff
- Old published events cleaned up daily (7 day retention)

---

### Fix 1.2: Catch JsonProcessingException in Kafka Listeners

**Problem**: Propagating JsonProcessingException causes infinite retry loops on malformed messages

**Solution**:
- Wrap objectMapper.readValue() in try-catch blocks in all @KafkaListener methods
- Catch JsonProcessingException, log error with message details, increment parse_error metric
- Return early without retrying (message goes to DLT via existing KafkaDltConfig)
- Remove `throws JsonProcessingException` from all listener method signatures

**Files**:
- MODIFY: `dispatch-service/src/main/java/com/vivek/dispatch/listener/DispatchListener.java`
- MODIFY: `ambulance-service/src/main/java/com/vivek/ambulance/listener/AmbulanceAssignmentListener.java`
- MODIFY: `tracking-service/src/main/java/com/vivek/tracking/listener/TrackingListener.java`
- MODIFY: `notification-service/src/main/java/com/vivek/notification/listener/NotificationListener.java`

**Validation**:
- Malformed JSON message → logged once, sent to DLT, no retry loop
- Valid JSON message → processed normally
- Metrics show parse_error.total counter increments for bad messages

---

### Fix 1.3: Fix requeueEmergency to Actually Re-enqueue

**Problem**: requeueEmergency deletes idempotency key but doesn't re-add emergency to queue, orphaning it

**Solution**:
- Modify `enqueueEmergency()` to save emergency payload to Redis with key `emergency:payload:{emergencyId}` (2 hour TTL)
- Modify `requeueEmergency()` to:
  1. Delete idempotency key
  2. Delete assignment ack key
  3. Retrieve saved payload from Redis
  4. Deserialize and call enqueueEmergency() to re-add to priority queue
  5. Log error and increment metric if payload not found (manual recovery needed)

**Files**:
- MODIFY: `dispatch-service/src/main/java/com/vivek/dispatch/service/DispatchEngine.java` (methods: enqueueEmergency, requeueEmergency)

**Validation**:
- Assignment rejected → emergency re-queued to correct priority queue
- Emergency dispatched to different ambulance on retry
- Payload not found → error logged, metric incremented, manual recovery triggered

---

### Fix 1.4: Remove synchronized, Use Lua Scripts for Atomicity

**Problem**: synchronized keyword only protects single JVM, useless in multi-instance deployment

**Solution**:
- Remove `synchronized` keyword from `AmbulanceStateTracker.transition()` method
- Implement Lua script for atomic version-checked state transition:
  ```lua
  local version = redis.call('GET', versionKey)
  if version ~= expectedVersion then return -1 end
  redis.call('SET', statusKey, nextStatus)
  redis.call('SET', versionKey, version + 1)
  redis.call('SET', lastUpdatedKey, now)
  return version + 1
  ```
- Use RedisScript with KEYS and ARGV for proper atomicity
- Keep `assignEmergencyAtomically()` as-is (already uses Lua script)

**Files**:
- MODIFY: `ambulance-service/src/main/java/com/vivek/ambulance/service/AmbulanceStateTracker.java` (method: transition)

**Validation**:
- Multiple service instances running → no race conditions on state transitions
- Version mismatch → transition rejected, metric incremented
- Successful transition → version incremented atomically

---

### Fix 1.5: Version-Aware State Transitions

**Problem**: Scheduled transitions use pre-computed version offsets (version+1, +2, +3) that silently fail if version changes

**Solution**:
- Modify scheduled transition lambdas to read current version from Redis at execution time (not at scheduling time)
- Add guard check: verify `activeEmergencyId` matches expected emergency before transitioning
- Implement `safeTransition()` helper method that:
  1. Reads current version from Redis
  2. Checks activeEmergencyId matches
  3. Calls transition() with current version
  4. Logs warning if transition fails (auto-heal will recover)
- After COMPLETED transition, immediately transition to AVAILABLE atomically

**Files**:
- MODIFY: `ambulance-service/src/main/java/com/vivek/ambulance/listener/AmbulanceAssignmentListener.java` (methods: consumeAssignment, safeTransition, completeTrip)

**Validation**:
- Version changes between scheduling and execution → transition uses correct current version
- Auto-heal changes activeEmergencyId → scheduled transition skipped with warning
- Ambulance completes trip → immediately becomes AVAILABLE for next assignment

---

## Serious Architectural Fixes (Priority 2)

### Fix 2.1: Externalize Credentials to .env File

**Problem**: Plaintext credentials committed to version control

**Solution**:
- Create `.env` file with: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB, KAFKA_CLUSTER_ID, CORS_ALLOWED_ORIGINS
- Create `.env.example` template with placeholder values
- Modify `docker-compose.yml` to reference ${POSTGRES_PASSWORD}, ${KAFKA_CLUSTER_ID}, etc.
- Add `.env` to `.gitignore` (keep `.env.example` tracked)
- Update README with instructions to copy .env.example to .env

**Files**:
- NEW: `.env` (gitignored)
- NEW: `.env.example`
- MODIFY: `docker-compose.yml`
- MODIFY: `.gitignore`
- MODIFY: `README.md`

**Validation**:
- .env file not tracked in git
- docker-compose up fails without .env file (expected)
- Services connect successfully with credentials from .env

---

### Fix 2.2: Remove Hardcoded Kafka CLUSTER_ID

**Problem**: Hardcoded CLUSTER_ID causes all environments to share same cluster identity

**Solution**:
- Replace `CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk` with `CLUSTER_ID: ${KAFKA_CLUSTER_ID}`
- Add KAFKA_CLUSTER_ID to .env file (unique per environment)
- Document in README that each environment should generate unique cluster ID

**Files**:
- MODIFY: `docker-compose.yml`
- MODIFY: `.env.example`

**Validation**:
- Each environment uses different CLUSTER_ID
- No cross-environment message leakage

---

### Fix 2.3: Profile-Based CORS Configuration

**Problem**: CORS only allows production origin, blocks localhost development

**Solution**:
- Modify `CorsConfig.java` to load allowed origins from `@Value("${cors.allowed-origins}")`
- Add `cors.allowed-origins` property to `application.yml` with default: `http://localhost:3000,http://localhost:3001,https://mobile-driver-app.onrender.com`
- Support comma-separated list of origins
- Allow override via CORS_ALLOWED_ORIGINS environment variable

**Files**:
- MODIFY: `api-gateway/src/main/java/com/vivek/gateway/config/CorsConfig.java`
- MODIFY: `api-gateway/src/main/resources/application.yml`

**Validation**:
- Localhost requests allowed in development
- Production origin still allowed
- Environment variable override works

---

### Fix 2.4: Restrict Actuator Endpoint Exposure

**Problem**: All Actuator endpoints exposed publicly including /shutdown, /env, /heapdump

**Solution**:
- Modify `application.yml` to set `management.endpoints.web.exposure.include: "health,info,prometheus,metrics"`
- Set `management.endpoint.health.show-details: when-authorized`
- Keep /actuator/health for load balancer health checks
- Document that sensitive endpoints require authentication in production

**Files**:
- MODIFY: `api-gateway/src/main/resources/application.yml`
- MODIFY: `emergency-service/src/main/resources/application.yml`
- MODIFY: `dispatch-service/src/main/resources/application.yml`
- MODIFY: `ambulance-service/src/main/resources/application.yml`

**Validation**:
- /actuator/health accessible without auth
- /actuator/shutdown returns 404
- /actuator/env returns 404

---

### Fix 2.5: Consolidate Hardcoded Fleet Configuration

**Problem**: Ambulance IDs hardcoded in multiple places with different values

**Solution**:
- Add `ambulance.fleet.ids` property to `application.yml` in ambulance-service: `AMB-101,AMB-102,AMB-103`
- Inject fleet IDs via `@Value("${ambulance.fleet.ids}")` in AmbulanceStateTracker and AmbulanceMovementSimulator
- Remove hardcoded arrays: `String[] AMBULANCES = {...}`
- Use single source of truth for all fleet operations (initialization, auto-heal, etc.)

**Files**:
- MODIFY: `ambulance-service/src/main/resources/application.yml`
- MODIFY: `ambulance-service/src/main/java/com/vivek/ambulance/service/AmbulanceStateTracker.java`
- MODIFY: `ambulance-service/src/main/java/com/vivek/ambulance/service/AmbulanceMovementSimulator.java`

**Validation**:
- All ambulances initialized from config
- Auto-heal covers all configured ambulances
- Adding new ambulance only requires config change

---

### Fix 2.6: Fix COMPLETED Status Availability Race

**Problem**: DispatchEngine treats COMPLETED as available but FSM hasn't transitioned to AVAILABLE yet

**Solution**:
- Modify `AmbulanceAssignmentListener.completeTrip()` to immediately transition COMPLETED → AVAILABLE atomically
- Modify `DispatchEngine.isAvailableInRedis()` to only check for "AVAILABLE" status (remove "COMPLETED" case)
- Ensure no window where ambulance is COMPLETED but not yet AVAILABLE

**Files**:
- MODIFY: `ambulance-service/src/main/java/com/vivek/ambulance/listener/AmbulanceAssignmentListener.java` (method: completeTrip)
- MODIFY: `dispatch-service/src/main/java/com/vivek/dispatch/service/DispatchEngine.java` (method: isAvailableInRedis)

**Validation**:
- Ambulance completes trip → immediately AVAILABLE
- No assignment rejections due to COMPLETED status
- Dispatch engine only assigns to AVAILABLE ambulances

---

## Design & Code Quality Fixes (Priority 3)

### Fix 3.1: Update Emergency Status in Postgres

**Problem**: Emergency status remains PENDING after dispatch, making status endpoint useless

**Solution**:
- Add `updateStatus()` method to EmergencyService with @Transactional
- Create Kafka listeners in emergency-service to consume:
  - `ambulance-assigned-topic` → update status to ASSIGNED, set assignedAmbulanceId and assignmentTimestamp
  - `ambulance-completed-topic` → update status to COMPLETED, set completedAt
- Add new listener class: `EmergencyStatusListener.java`

**Files**:
- MODIFY: `emergency-service/src/main/java/com/vivek/emergency/service/EmergencyService.java` (add updateStatus method)
- NEW: `emergency-service/src/main/java/com/vivek/emergency/listener/EmergencyStatusListener.java`

**Validation**:
- Emergency created → status PENDING
- Ambulance assigned → status ASSIGNED
- Trip completed → status COMPLETED
- GET /emergency/status/ASSIGNED returns correct emergencies

---

### Fix 3.2: Replace Recursion with Iteration in Movement Simulator

**Problem**: Recursive moveAlongRoute risks StackOverflowError on dense waypoints

**Solution**:
- Replace recursive call with while-loop in `moveAlongRoute()` method
- Process at most one waypoint per scheduler tick
- When waypoint reached, update currentWaypointIndex and continue loop (don't recurse)
- Exit loop when destination reached or movement step incomplete

**Files**:
- MODIFY: `ambulance-service/src/main/java/com/vivek/ambulance/service/AmbulanceMovementSimulator.java` (method: moveAlongRoute)

**Validation**:
- Dense waypoint routes processed without stack overflow
- Ambulance movement smooth and continuous
- ETA calculations remain accurate

---

### Fix 3.3: Move Initialization to @PostConstruct

**Problem**: initializeAmbulance called inside @Scheduled loop, wasting CPU

**Solution**:
- Create `@PostConstruct initializeFleet()` method in AmbulanceMovementSimulator
- Move all initializeAmbulance() calls from updateAmbulanceLocations() to initializeFleet()
- Modify updateAmbulanceLocations() to iterate over currentLocations.keySet() instead

**Files**:
- MODIFY: `ambulance-service/src/main/java/com/vivek/ambulance/service/AmbulanceMovementSimulator.java` (methods: initializeFleet, updateAmbulanceLocations)

**Validation**:
- Ambulances initialized once at startup
- @Scheduled method only processes movement updates
- No redundant containsKey checks

---

### Fix 3.4: Use EmergencyId-Based Queue Removal

**Problem**: acknowledgeRawPayload uses value-based matching, could dequeue wrong emergency

**Solution**:
- Modify `acknowledgeEmergency()` to use Redis LREM command with emergencyId-based matching
- Parse emergencyId from payload before removal
- Target specific queue key (high/medium/low) instead of searching all queues
- Add validation that removed emergency matches expected emergencyId

**Files**:
- MODIFY: `dispatch-service/src/main/java/com/vivek/dispatch/service/DispatchEngine.java` (method: acknowledgeEmergency)

**Validation**:
- Correct emergency removed from queue
- No accidental removal of different emergency with similar payload
- Queue integrity maintained

---

## Minor Fixes (Priority 4)

### Fix 4.1: Gitignore error.txt Files

**Problem**: error.txt files committed to version control

**Solution**:
- Add `error.txt` to `.gitignore`
- Remove existing error.txt files from git: `git rm error.txt init-db/error.txt`

**Files**:
- MODIFY: `.gitignore`
- DELETE: `error.txt`
- DELETE: `init-db/error.txt`

---

### Fix 4.2: Fix Docker Network Configuration

**Problem**: tracking-service and notification-service missing from dispatch-network

**Solution**:
- Add `networks: [dispatch-network]` to tracking-service and notification-service in docker-compose.yml
- Ensure all services can reach Redis and Kafka

**Files**:
- MODIFY: `docker-compose.yml`

---

### Fix 4.3: Add @Transactional Annotations

**Problem**: Status update methods risk partial writes without transactions

**Solution**:
- Add `@Transactional` to EmergencyService.updateStatus()
- Add `@Transactional` to any other status update methods across services
- Ensure automatic rollback on exceptions

**Files**:
- MODIFY: `emergency-service/src/main/java/com/vivek/emergency/service/EmergencyService.java`

---

## Implementation Order

1. **Phase 1 - Critical Fixes** (Fixes 1.1-1.5): Prevent data loss and system failures
2. **Phase 2 - Security & Config** (Fixes 2.1-2.4): Externalize secrets, restrict endpoints
3. **Phase 3 - Architectural Improvements** (Fixes 2.5-2.6, 3.1-3.4): Consolidate config, fix races, improve code quality
4. **Phase 4 - Cleanup** (Fixes 4.1-4.3): Minor fixes and cleanup

## Testing Strategy

- Unit tests for Outbox pattern (OutboxPublisher retry logic)
- Integration tests for requeue flow (assignment rejection → re-dispatch)
- Load tests for multi-instance deployment (verify Lua script atomicity)
- Security tests for Actuator endpoints (verify restricted access)
- End-to-end tests for emergency lifecycle (PENDING → ASSIGNED → COMPLETED)

## Rollback Plan

- Database migrations are reversible (V2__create_outbox_events has corresponding down migration)
- Feature flags for Outbox pattern (can fall back to direct Kafka publish)
- Config changes are backward compatible (old configs still work with defaults)
- Gradual rollout: deploy to staging first, monitor metrics, then production
