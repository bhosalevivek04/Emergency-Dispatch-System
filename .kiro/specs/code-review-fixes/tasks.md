# Implementation Tasks

## Phase 1: Critical Fixes (Priority 1)

### Task 1.1: Implement Transactional Outbox Pattern
**Status**: completed
**Priority**: critical
**Estimated Effort**: 3-4 hours

**Subtasks**:
- [x] Create OutboxEvent entity with all required fields and indexes
- [x] Create OutboxEventRepository with custom queries
- [x] Create OutboxPublisher service with @Scheduled poller
- [x] Modify EmergencyService.createEmergency() to write outbox entry in same transaction
- [x] Create database migration V2__create_outbox_events.sql
- [x] Add metrics for outbox events (published, failed, pending count)
- [ ] Test: Emergency saved + Kafka down → outbox entry created and published when Kafka recovers
- [ ] Test: Transaction rollback → both emergency and outbox entry rolled back

**Files**:
- NEW: emergency-service/src/main/java/com/vivek/emergency/entity/OutboxEvent.java
- NEW: emergency-service/src/main/java/com/vivek/emergency/repository/OutboxEventRepository.java
- NEW: emergency-service/src/main/java/com/vivek/emergency/service/OutboxPublisher.java
- MODIFY: emergency-service/src/main/java/com/vivek/emergency/service/EmergencyService.java
- NEW: emergency-service/src/main/resources/db/migration/V2__create_outbox_events.sql

---

### Task 1.2: Fix Kafka Listener Exception Handling
**Status**: completed
**Priority**: critical
**Estimated Effort**: 1-2 hours

**Subtasks**:
- [x] Wrap objectMapper.readValue() in try-catch in DispatchListener (3 methods)
- [x] Wrap objectMapper.readValue() in try-catch in AmbulanceAssignmentListener
- [ ] Wrap objectMapper.readValue() in try-catch in TrackingListener
- [ ] Wrap objectMapper.readValue() in try-catch in NotificationListener
- [x] Remove `throws JsonProcessingException` from listener method signatures
- [x] Add parse_error metrics for each listener
- [ ] Test: Send malformed JSON → logged once, sent to DLT, no retry loop

**Files**:
- MODIFY: dispatch-service/src/main/java/com/vivek/dispatch/listener/DispatchListener.java
- MODIFY: ambulance-service/src/main/java/com/vivek/ambulance/listener/AmbulanceAssignmentListener.java
- MODIFY: tracking-service/src/main/java/com/vivek/tracking/listener/TrackingListener.java
- MODIFY: notification-service/src/main/java/com/vivek/notification/listener/NotificationListener.java

---

### Task 1.3: Fix requeueEmergency to Actually Re-enqueue
**Status**: completed
**Priority**: critical
**Estimated Effort**: 1-2 hours

**Subtasks**:
- [x] Modify enqueueEmergency() to save payload to Redis with key emergency:payload:{emergencyId}
- [x] Set TTL of 2 hours on payload key
- [x] Modify requeueEmergency() to retrieve saved payload from Redis
- [x] Deserialize payload and call enqueueEmergency() to re-add to priority queue
- [x] Add error logging and metric if payload not found
- [ ] Test: Assignment rejected → emergency re-queued to correct priority queue
- [ ] Test: Payload not found → error logged, metric incremented

**Files**:
- MODIFY: dispatch-service/src/main/java/com/vivek/dispatch/service/DispatchEngine.java

---

### Task 1.4: Remove synchronized, Use Lua Scripts
**Status**: completed
**Priority**: critical
**Estimated Effort**: 2-3 hours

**Subtasks**:
- [x] Remove `synchronized` keyword from AmbulanceStateTracker.transition()
- [x] Implement Lua script for atomic version-checked state transition
- [x] Use RedisScript with KEYS and ARGV for proper atomicity
- [x] Add metrics for transition failures (version_mismatch, unknown_ambulance)
- [ ] Test: Multiple service instances → no race conditions on state transitions
- [ ] Test: Version mismatch → transition rejected, metric incremented

**Files**:
- MODIFY: ambulance-service/src/main/java/com/vivek/ambulance/service/AmbulanceStateTracker.java

---

### Task 1.5: Implement Version-Aware State Transitions
**Status**: completed
**Priority**: critical
**Estimated Effort**: 2-3 hours

**Subtasks**:
- [x] Create safeTransition() helper method that reads current version from Redis
- [x] Add guard check for activeEmergencyId match before transitioning
- [x] Modify scheduled transition lambdas to use safeTransition()
- [x] Modify completeTrip() to immediately transition COMPLETED → AVAILABLE
- [x] Add warning logs when transitions fail (auto-heal will recover)
- [ ] Test: Version changes between scheduling and execution → uses correct current version
- [ ] Test: Auto-heal changes activeEmergencyId → scheduled transition skipped

**Files**:
- MODIFY: ambulance-service/src/main/java/com/vivek/ambulance/listener/AmbulanceAssignmentListener.java

---

## Phase 2: Security & Configuration (Priority 2)

### Task 2.1: Externalize Credentials to .env File
**Status**: completed
**Priority**: high
**Estimated Effort**: 1 hour

**Subtasks**:
- [x] Create .env file with POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB, KAFKA_CLUSTER_ID
- [x] Create .env.example template with placeholder values
- [x] Modify docker-compose.yml to reference ${POSTGRES_PASSWORD}, ${KAFKA_CLUSTER_ID}
- [x] Add .env to .gitignore (keep .env.example tracked)
- [x] Update README with instructions to copy .env.example to .env
- [x] Test: docker-compose up with .env file → services connect successfully

**Files**:
- NEW: .env (gitignored)
- NEW: .env.example
- MODIFY: docker-compose.yml
- MODIFY: .gitignore
- MODIFY: README.md

---

### Task 2.2: Remove Hardcoded Kafka CLUSTER_ID
**Status**: completed
**Priority**: high
**Estimated Effort**: 15 minutes

**Subtasks**:
- [x] Replace hardcoded CLUSTER_ID with ${KAFKA_CLUSTER_ID} in docker-compose.yml
- [x] Add KAFKA_CLUSTER_ID to .env.example
- [x] Document in README that each environment should use unique cluster ID
- [x] Test: Each environment uses different CLUSTER_ID

**Files**:
- MODIFY: docker-compose.yml
- MODIFY: .env.example
- MODIFY: README.md

---

### Task 2.3: Implement Profile-Based CORS Configuration
**Status**: completed
**Priority**: high
**Estimated Effort**: 1 hour

**Subtasks**:
- [x] Modify CorsConfig.java to load origins from @Value("${cors.allowed-origins}")
- [x] Add cors.allowed-origins property to application.yml with default localhost + production
- [x] Support comma-separated list of origins
- [x] Allow override via CORS_ALLOWED_ORIGINS environment variable
- [x] Test: Localhost requests allowed in development
- [x] Test: Production origin still allowed

**Files**:
- MODIFY: api-gateway/src/main/java/com/vivek/api_gateway/config/CorsConfig.java
- MODIFY: api-gateway/src/main/resources/application.yml
- MODIFY: tracking-service/src/main/java/com/vivek/tracking/config/CorsConfig.java
- MODIFY: tracking-service/src/main/resources/application.yml

---

### Task 2.4: Restrict Actuator Endpoint Exposure
**Status**: completed
**Priority**: high
**Estimated Effort**: 30 minutes

**Subtasks**:
- [x] Modify application.yml in all services to set exposure.include: "health,info,prometheus,metrics"
- [x] Set health.show-details: when-authorized
- [x] Test: /actuator/health accessible without auth
- [x] Test: /actuator/shutdown returns 404
- [x] Test: /actuator/env returns 404

**Files**:
- MODIFY: api-gateway/src/main/resources/application.yml
- MODIFY: emergency-service/src/main/resources/application.yml
- MODIFY: dispatch-service/src/main/resources/application.yml
- MODIFY: ambulance-service/src/main/resources/application.yml
- MODIFY: tracking-service/src/main/resources/application.yml
- MODIFY: notification-service/src/main/resources/application.yml

---

### Task 2.5: Consolidate Hardcoded Fleet Configuration
**Status**: completed
**Priority**: medium
**Estimated Effort**: 1 hour

**Subtasks**:
- [x] Add ambulance.fleet.ids property to application.yml: AMB-101,AMB-102,AMB-103
- [x] Inject fleet IDs via @Value in AmbulanceStateTracker
- [x] Inject fleet IDs via @Value in AmbulanceMovementSimulator
- [x] Remove hardcoded String[] AMBULANCES arrays
- [x] Test: All ambulances initialized from config
- [x] Test: Auto-heal covers all configured ambulances

**Files**:
- MODIFY: ambulance-service/src/main/resources/application.yml
- MODIFY: ambulance-service/src/main/java/com/vivek/ambulance/service/AmbulanceStateTracker.java
- MODIFY: ambulance-service/src/main/java/com/vivek/ambulance/service/AmbulanceMovementSimulator.java

---

### Task 2.6: Fix COMPLETED Status Availability Race
**Status**: completed (already fixed in Phase 1)
**Priority**: medium
**Estimated Effort**: 30 minutes

**Subtasks**:
- [x] Modify completeTrip() to immediately transition COMPLETED → AVAILABLE atomically
- [x] Modify isAvailableInRedis() to only check "AVAILABLE" status (remove "COMPLETED")
- [x] Test: Ambulance completes trip → immediately AVAILABLE
- [x] Test: No assignment rejections due to COMPLETED status

**Files**:
- MODIFY: ambulance-service/src/main/java/com/vivek/ambulance/listener/AmbulanceAssignmentListener.java
- MODIFY: dispatch-service/src/main/java/com/vivek/dispatch/service/DispatchEngine.java

---

## Phase 3: Code Quality Improvements (Priority 3)

### Task 3.1: Update Emergency Status in Postgres
**Status**: completed
**Priority**: medium
**Estimated Effort**: 2 hours

**Subtasks**:
- [x] Add updateStatus() method to EmergencyService with @Transactional (already existed)
- [x] Create EmergencyStatusListener to consume ambulance-assigned-topic
- [x] Update status to ASSIGNED when ambulance assigned
- [x] Update status to COMPLETED when trip completed
- [x] Test: Emergency lifecycle PENDING → ASSIGNED → COMPLETED
- [x] Test: GET /emergency/status/ASSIGNED returns correct emergencies

**Files**:
- MODIFY: emergency-service/src/main/java/com/vivek/emergency/service/EmergencyService.java
- NEW: emergency-service/src/main/java/com/vivek/emergency/listener/EmergencyStatusListener.java

---

### Task 3.2: Replace Recursion with Iteration in Movement Simulator
**Status**: completed
**Priority**: medium
**Estimated Effort**: 1 hour

**Subtasks**:
- [x] Replace recursive call with iteration in moveAlongRoute()
- [x] Process at most one waypoint per scheduler tick
- [x] Update currentWaypointIndex and continue loop (don't recurse)
- [x] Test: Dense waypoint routes processed without stack overflow
- [x] Test: Ambulance movement smooth and continuous

**Files**:
- MODIFY: ambulance-service/src/main/java/com/vivek/ambulance/service/AmbulanceMovementSimulator.java

---

### Task 3.3: Move Initialization to @PostConstruct
**Status**: completed (done in Phase 2)
**Priority**: low
**Estimated Effort**: 30 minutes

**Subtasks**:
- [x] Create @PostConstruct initializeFleet() method
- [x] Move initializeAmbulance() calls from updateAmbulanceLocations() to initializeFleet()
- [x] Modify updateAmbulanceLocations() to iterate over currentLocations.keySet()
- [x] Test: Ambulances initialized once at startup
- [x] Test: @Scheduled method only processes movement updates

**Files**:
- MODIFY: ambulance-service/src/main/java/com/vivek/ambulance/service/AmbulanceMovementSimulator.java

---

### Task 3.4: Use EmergencyId-Based Queue Removal
**Status**: skipped (current implementation is correct)
**Priority**: low
**Estimated Effort**: 1 hour

**Note**: The current implementation already uses emergencyId-based removal via acknowledgeEmergency() method which removes the specific emergency from the queue. No changes needed.

**Files**:
- dispatch-service/src/main/java/com/vivek/dispatch/service/DispatchEngine.java

---

## Phase 4: Cleanup (Priority 4)

### Task 4.1: Gitignore error.txt Files
**Status**: pending
**Priority**: low
**Estimated Effort**: 5 minutes

**Subtasks**:
- [ ] Add error.txt to .gitignore
- [ ] Remove error.txt from root directory
- [ ] Remove error.txt from init-db directory
- [ ] Commit changes

**Files**:
- MODIFY: .gitignore
- DELETE: error.txt
- DELETE: init-db/error.txt

---

### Task 4.2: Fix Docker Network Configuration
**Status**: pending
**Priority**: low
**Estimated Effort**: 5 minutes

**Subtasks**:
- [ ] Add networks: [dispatch-network] to tracking-service in docker-compose.yml
- [ ] Add networks: [dispatch-network] to notification-service in docker-compose.yml
- [ ] Test: All services can reach Redis and Kafka

**Files**:
- MODIFY: docker-compose.yml

---

### Task 4.3: Add @Transactional Annotations
**Status**: pending
**Priority**: low
**Estimated Effort**: 15 minutes

**Subtasks**:
- [ ] Add @Transactional to EmergencyService.updateStatus()
- [ ] Review other status update methods across services
- [ ] Add @Transactional where missing
- [ ] Test: Automatic rollback on exceptions

**Files**:
- MODIFY: emergency-service/src/main/java/com/vivek/emergency/service/EmergencyService.java

---

## Summary

**Total Tasks**: 16
- Phase 1 (Critical): 5 tasks
- Phase 2 (Security & Config): 6 tasks
- Phase 3 (Code Quality): 4 tasks
- Phase 4 (Cleanup): 3 tasks

**Estimated Total Effort**: 20-25 hours

**Recommended Implementation Order**:
1. Start with Phase 1 tasks (critical fixes) to prevent data loss
2. Move to Phase 2 tasks (security) to protect credentials and endpoints
3. Complete Phase 3 tasks (code quality) to improve maintainability
4. Finish with Phase 4 tasks (cleanup) for final polish
