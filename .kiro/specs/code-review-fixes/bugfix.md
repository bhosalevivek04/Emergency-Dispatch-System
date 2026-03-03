# Bugfix Requirements Document

## Introduction

This document addresses 22 code review issues identified in the Emergency Dispatch System, a microservices-based application using Spring Boot, Kafka, Redis, and PostgreSQL. The issues range from critical data loss bugs to architectural flaws and code quality problems. The fixes are prioritized by severity: Critical (5 issues causing data loss/system failures), Serious Architectural (6 issues affecting reliability/security), Design & Code Quality (7 issues affecting maintainability), and Minor (4 cleanup issues).

The system consists of 6 microservices: emergency-service, dispatch-service, ambulance-service, tracking-service, notification-service, and api-gateway. The bugs span distributed systems correctness (dual-write, idempotency), concurrency safety (false atomicity), message processing (infinite retry loops), state management (orphaned emergencies, stuck ambulances), and security (exposed credentials, open endpoints).

## Bug Analysis

### Section 1: Current Behavior (Defect)

#### 1.1 Critical Bugs - Data Loss & System Failures

1.1.1 WHEN EmergencyService saves an emergency to Postgres and the subsequent Kafka publish fails THEN the system returns HTTP 200 to the caller but the emergency is never dispatched, causing silent data loss

1.1.2 WHEN a Kafka listener receives a malformed JSON message that causes JsonProcessingException THEN the system enters an infinite retry loop burning CPU until DLT timeout, instead of immediately dead-lettering the message

1.1.3 WHEN DispatchEngine.requeueEmergency is called after assignment rejection THEN the system deletes the idempotency key but does not re-add the emergency to any Redis queue, orphaning the emergency permanently with no dispatch path

1.1.4 WHEN multiple instances of ambulance-service run concurrently and synchronized methods (transition, assignEmergencyAtomically) are called THEN the system provides false atomicity protection only within a single JVM, allowing race conditions across instances in production

1.1.5 WHEN AmbulanceAssignmentListener schedules future state transitions with hardcoded version increments (assignedExpectedVersion + 1, +2, +3) and any intervening event changes the ambulance version THEN all scheduled transitions silently fail with no retry or alert, causing the ambulance to get stuck in an intermediate state

#### 1.2 Serious Architectural Issues - Reliability & Security

1.2.1 WHEN docker-compose.yml is used to deploy the system THEN plaintext credentials (POSTGRES_PASSWORD: dispatch_password) are committed to version control and exposed in public repositories

1.2.2 WHEN docker-compose.yml is reused across environments THEN the hardcoded Kafka CLUSTER_ID causes all environments to share the same cluster identity, creating cross-environment interference

1.2.3 WHEN a developer attempts local development against the API gateway THEN CORS blocks all requests except from the hardcoded production origin (https://mobile-driver-app.onrender.com), preventing localhost access

1.2.4 WHEN DispatchEngine restarts (cold start) THEN the in-memory ConcurrentHashMap ambulance state cache is empty, causing findNearestAvailable to return null for up to 10 seconds until ambulances re-broadcast locations

1.2.5 WHEN the system initializes ambulance fleet data THEN hardcoded arrays exist in multiple places with different values (["AMB-101", "AMB-102", "AMB-103"] vs ["A1", "A2", "A3"]), causing inconsistent state and incomplete auto-heal coverage

1.2.6 WHEN DispatchEngine considers a COMPLETED ambulance as available but the ambulance-service hasn't yet transitioned COMPLETED → AVAILABLE THEN the system assigns the emergency to a COMPLETED ambulance, the FSM rejects it, and the emergency remains unserved until retry

#### 1.3 Design & Code Quality Issues - Maintainability

1.3.1 WHEN an emergency is dispatched and assigned to an ambulance THEN the emergency status in Postgres remains permanently as PENDING, making the GET /emergency/status/{status} endpoint useless for tracking dispatch progress

1.3.2 WHEN AmbulanceMovementSimulator.moveAlongRoute reaches a waypoint and recursively calls itself in the same scheduler tick THEN the system risks StackOverflowError on routes with dense waypoints relative to ambulance speed

1.3.3 WHEN NotificationListener consumes ambulance-assigned-topic messages THEN the system only logs the event without sending any actual notifications (SMS, push, email, webhook), making the notification-service non-functional

1.3.4 WHEN the @Scheduled updateAmbulanceLocations method runs every 1 second THEN the system calls initializeAmbulance for each ambulance inside the loop, wasting CPU on redundant containsKey checks instead of using @PostConstruct

1.3.5 WHEN DispatchEngine.acknowledgeRawPayload removes an emergency by value match across all three Redis queues THEN the system could dequeue the wrong emergency if two emergencies have identical JSON serialization

1.3.6 WHEN developers run the test suite for dispatch-service, ambulance-service, tracking-service, notification-service, or api-gateway THEN only empty contextLoads() tests execute with no business logic coverage for dispatch algorithm, state machine, idempotency, auto-heal, or ACK flow

1.3.7 WHEN the api-gateway is deployed with management.endpoints.web.exposure.include: "*" THEN all Actuator endpoints including /actuator/shutdown, /actuator/env, and /actuator/heapdump are publicly exposed without authentication

#### 1.4 Minor Issues - Cleanup

1.4.1 WHEN the repository is committed THEN error.txt files exist in both root and init-db/ directories in version control instead of being gitignored

1.4.2 WHEN docker-compose variants are used THEN tracking-service and notification-service are missing from dispatch-network in some configurations, preventing Redis/Kafka connectivity

1.4.3 WHEN EmergencyEvent DTO fields are modified THEN developers must manually update duplicated copies across 4 services (emergency-service, dispatch-service, ambulance-service, notification-service) with no shared module

1.4.4 WHEN status update methods are called without @Transactional annotation THEN the system risks partial writes if exceptions occur mid-update

### Section 2: Expected Behavior (Correct)

#### 2.1 Critical Bugs - Data Loss & System Failures

2.1.1 WHEN EmergencyService saves an emergency to Postgres and the subsequent Kafka publish fails THEN the system SHALL implement the Transactional Outbox pattern to ensure atomic dual-write, with a background poller publishing outbox records to Kafka and marking them as sent

2.1.2 WHEN a Kafka listener receives a malformed JSON message that causes JsonProcessingException THEN the system SHALL catch the exception, log the error with message details, and immediately send the message to a dead-letter topic without retrying

2.1.3 WHEN DispatchEngine.requeueEmergency is called after assignment rejection THEN the system SHALL delete the idempotency key AND re-add the emergency to the appropriate Redis queue (high/medium/low priority) to ensure it gets dispatched

2.1.4 WHEN multiple instances of ambulance-service run concurrently THEN the system SHALL remove misleading synchronized keywords and rely exclusively on Redis Lua scripts for true distributed atomicity across all instances

2.1.5 WHEN AmbulanceAssignmentListener schedules future state transitions THEN the system SHALL implement version-aware retry logic that detects version mismatches, logs failures, and triggers compensating actions (alerts or re-scheduling) instead of silently failing

#### 2.2 Serious Architectural Issues - Reliability & Security

2.2.1 WHEN docker-compose.yml is used to deploy the system THEN the system SHALL externalize all credentials to environment variables or .env files with .env added to .gitignore, and docker-compose SHALL reference ${POSTGRES_PASSWORD} placeholders

2.2.2 WHEN docker-compose.yml is used across environments THEN the system SHALL remove the hardcoded CLUSTER_ID and allow Kafka to generate unique cluster identities per environment, or use environment-specific configuration files

2.2.3 WHEN a developer attempts local development against the API gateway THEN the system SHALL implement profile-based CORS configuration with @Profile("local") allowing localhost:* origins and @Profile("prod") restricting to production origins

2.2.4 WHEN DispatchEngine starts up THEN the system SHALL implement a Redis-backed ambulance state cache that persists across restarts, or implement a startup synchronization mechanism that queries ambulance-service for current states before accepting emergencies

2.2.5 WHEN the system initializes ambulance fleet data THEN the system SHALL consolidate all hardcoded ambulance arrays into a single configuration source (application.yml property or database table) referenced by all services

2.2.6 WHEN DispatchEngine considers ambulance availability THEN the system SHALL either exclude COMPLETED status from availability checks OR ensure ambulance-service immediately transitions COMPLETED → AVAILABLE atomically before acknowledging completion

#### 2.3 Design & Code Quality Issues - Maintainability

2.3.1 WHEN an emergency is dispatched and assigned to an ambulance THEN the system SHALL update the emergency status in Postgres through event listeners (PENDING → ASSIGNED → IN_PROGRESS → COMPLETED) to maintain accurate database state

2.3.2 WHEN AmbulanceMovementSimulator.moveAlongRoute reaches a waypoint THEN the system SHALL use iterative processing instead of recursion, processing at most one waypoint per scheduler tick to prevent stack overflow

2.3.3 WHEN NotificationListener consumes ambulance-assigned-topic messages THEN the system SHALL implement actual notification delivery via SMS gateway, push notification service, email service, or webhook, or remove the notification-service if not needed

2.3.4 WHEN the ambulance-service initializes THEN the system SHALL move initializeAmbulance calls to a @PostConstruct method that runs once at startup instead of inside the @Scheduled loop

2.3.5 WHEN DispatchEngine.acknowledgeRawPayload removes an emergency THEN the system SHALL use emergencyId-based removal with Redis LREM command targeting specific queue keys instead of value-based matching across all queues

2.3.6 WHEN developers run the test suite THEN the system SHALL include integration tests for dispatch algorithm (nearest ambulance selection), state machine transitions (all FSM paths), idempotency checks (duplicate emergency handling), auto-heal (stale assignment recovery), and ACK flow (rejection requeue)

2.3.7 WHEN the api-gateway is deployed THEN the system SHALL restrict Actuator endpoint exposure to only health and info endpoints in production (management.endpoints.web.exposure.include: "health,info") with authentication required for sensitive endpoints

#### 2.4 Minor Issues - Cleanup

2.4.1 WHEN the repository is committed THEN the system SHALL add error.txt to .gitignore and remove existing error.txt files from version control

2.4.2 WHEN docker-compose variants are used THEN the system SHALL ensure all services (tracking-service, notification-service) are included in dispatch-network configuration for proper Redis/Kafka connectivity

2.4.3 WHEN EmergencyEvent DTO is used across services THEN the system SHALL create a common-dto Maven module with shared DTOs and add it as a dependency to all services that need EmergencyEvent

2.4.4 WHEN status update methods are called THEN the system SHALL add @Transactional annotations to ensure atomic database updates with automatic rollback on exceptions

### Section 3: Unchanged Behavior (Regression Prevention)

#### 3.1 Critical Bugs - Data Loss & System Failures

3.1.1 WHEN EmergencyService successfully saves an emergency to Postgres and successfully publishes to Kafka THEN the system SHALL CONTINUE TO return HTTP 200 and the emergency SHALL CONTINUE TO be dispatched normally

3.1.2 WHEN a Kafka listener receives a valid JSON message THEN the system SHALL CONTINUE TO process the message successfully and update state as expected

3.1.3 WHEN an emergency is successfully assigned and acknowledged THEN the system SHALL CONTINUE TO remove it from the Redis queue and mark the idempotency key as processed

3.1.4 WHEN ambulance-service runs as a single instance in development THEN the system SHALL CONTINUE TO handle state transitions correctly without introducing new race conditions

3.1.5 WHEN scheduled state transitions execute with correct version numbers THEN the system SHALL CONTINUE TO transition ambulances through ASSIGNED → ON_ROUTE → ARRIVED → COMPLETED states successfully

#### 3.2 Serious Architectural Issues - Reliability & Security

3.2.1 WHEN docker-compose.yml is used with externalized credentials THEN the system SHALL CONTINUE TO connect to Postgres, Redis, and Kafka successfully with the same connection behavior

3.2.2 WHEN Kafka cluster identity is environment-specific THEN the system SHALL CONTINUE TO process messages within each environment without cross-environment message leakage

3.2.3 WHEN production requests come from the whitelisted origin THEN the system SHALL CONTINUE TO allow CORS requests from https://mobile-driver-app.onrender.com

3.2.4 WHEN DispatchEngine has warm cache with ambulance locations THEN the system SHALL CONTINUE TO find nearest available ambulances and dispatch emergencies efficiently

3.2.5 WHEN ambulance fleet configuration is centralized THEN the system SHALL CONTINUE TO initialize all ambulances and include them in auto-heal coverage

3.2.6 WHEN an ambulance is in AVAILABLE status THEN the system SHALL CONTINUE TO assign emergencies to it successfully

#### 3.3 Design & Code Quality Issues - Maintainability

3.3.1 WHEN emergency status updates are implemented THEN the system SHALL CONTINUE TO allow GET /emergency/{id} queries to return emergency details with accurate status

3.3.2 WHEN AmbulanceMovementSimulator processes waypoints iteratively THEN the system SHALL CONTINUE TO move ambulances along routes with correct position updates and ETA calculations

3.3.3 WHEN notification-service is enhanced or removed THEN the system SHALL CONTINUE TO dispatch emergencies and assign ambulances without depending on notification delivery

3.3.4 WHEN initializeAmbulance is moved to @PostConstruct THEN the system SHALL CONTINUE TO initialize all ambulances with correct starting locations before the first scheduler tick

3.3.5 WHEN acknowledgeRawPayload uses emergencyId-based removal THEN the system SHALL CONTINUE TO remove the correct emergency from the queue after successful assignment

3.3.6 WHEN comprehensive tests are added THEN the system SHALL CONTINUE TO pass all existing tests and maintain backward compatibility with current behavior

3.3.7 WHEN Actuator endpoints are restricted THEN the system SHALL CONTINUE TO expose /actuator/health for load balancer health checks and monitoring

#### 3.4 Minor Issues - Cleanup

3.4.1 WHEN error.txt is gitignored THEN the system SHALL CONTINUE TO allow developers to create local error.txt files for debugging without committing them

3.4.2 WHEN docker-compose network configuration is fixed THEN the system SHALL CONTINUE TO allow all services to communicate via Kafka topics and Redis operations

3.4.3 WHEN EmergencyEvent DTO is shared via common-dto module THEN the system SHALL CONTINUE TO serialize/deserialize events correctly across all Kafka topics

3.4.4 WHEN @Transactional annotations are added THEN the system SHALL CONTINUE TO update status fields successfully while gaining automatic rollback protection
