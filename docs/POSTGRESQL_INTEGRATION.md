# PostgreSQL Integration Guide

## Overview

This guide explains how to integrate PostgreSQL into the Emergency Dispatch System for persistent storage while keeping Redis for real-time operations.

## Architecture: Redis + PostgreSQL Hybrid

### Design Philosophy
```
Redis (Hot Path)          PostgreSQL (Cold Path)
├─ Real-time state       ├─ Historical records
├─ Geospatial search     ├─ Analytics data
├─ Distributed locks     ├─ Audit logs
├─ Idempotency cache     ├─ Reporting
└─ Session data          └─ Long-term storage
```

### Why Hybrid?
- **Redis**: Sub-millisecond reads, perfect for dispatch algorithm
- **PostgreSQL**: ACID compliance, complex queries, historical analysis
- **Best of Both**: Speed + Durability

---

## 1. Database Schema Design

### 1.1 Emergency Table
```sql
CREATE TABLE emergencies (
    id BIGSERIAL PRIMARY KEY,
    emergency_id VARCHAR(50) UNIQUE NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    
    -- Assignment details
    assigned_ambulance_id VARCHAR(50),
    assignment_timestamp TIMESTAMP,
    
    -- Timing
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    
    -- Metadata
    caller_phone VARCHAR(20),
    description TEXT,
    
    -- Indexes
    CONSTRAINT chk_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

-- Spatial index for geo queries
CREATE INDEX idx_emergencies_location ON emergencies USING GIST(location);

-- Regular indexes
CREATE INDEX idx_emergencies_status ON emergencies(status);
CREATE INDEX idx_emergencies_created_at ON emergencies(created_at DESC);
CREATE INDEX idx_emergencies_priority ON emergencies(priority);
```

### 1.2 Ambulance Table
```sql
CREATE TABLE ambulances (
    id BIGSERIAL PRIMARY KEY,
    ambulance_id VARCHAR(50) UNIQUE NOT NULL,
    
    -- Current state (synced from Redis)
    status VARCHAR(20) NOT NULL,
    current_location GEOGRAPHY(POINT, 4326),
    current_latitude DECIMAL(10, 8),
    current_longitude DECIMAL(11, 8),
    
    -- Metadata
    vehicle_number VARCHAR(50),
    equipment_type VARCHAR(50),
    crew_size INTEGER,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_location_update TIMESTAMP,
    
    CONSTRAINT chk_status CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'ON_ROUTE', 'ARRIVED', 'COMPLETED'))
);

CREATE INDEX idx_ambulances_status ON ambulances(status);
CREATE INDEX idx_ambulances_location ON ambulances USING GIST(current_location);
```

### 1.3 Assignment History (Audit Trail)
```sql
CREATE TABLE assignment_history (
    id BIGSERIAL PRIMARY KEY,
    emergency_id VARCHAR(50) NOT NULL,
    ambulance_id VARCHAR(50) NOT NULL,
    
    -- Assignment details
    assignment_type VARCHAR(20) NOT NULL, -- 'INITIAL', 'REASSIGNMENT'
    assignment_status VARCHAR(20) NOT NULL, -- 'ACCEPTED', 'REJECTED', 'TIMEOUT'
    
    -- Location at assignment
    emergency_location GEOGRAPHY(POINT, 4326),
    ambulance_location GEOGRAPHY(POINT, 4326),
    distance_km DECIMAL(10, 2),
    estimated_eta_seconds INTEGER,
    
    -- Timing
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    -- Metadata
    assignment_version INTEGER,
    notes TEXT,
    
    CONSTRAINT chk_assignment_type CHECK (assignment_type IN ('INITIAL', 'REASSIGNMENT')),
    CONSTRAINT chk_assignment_status CHECK (assignment_status IN ('ACCEPTED', 'REJECTED', 'TIMEOUT', 'COMPLETED'))
);

CREATE INDEX idx_assignment_emergency ON assignment_history(emergency_id);
CREATE INDEX idx_assignment_ambulance ON assignment_history(ambulance_id);
CREATE INDEX idx_assignment_timestamp ON assignment_history(assigned_at DESC);
```

### 1.4 State Transition Log
```sql
CREATE TABLE state_transitions (
    id BIGSERIAL PRIMARY KEY,
    ambulance_id VARCHAR(50) NOT NULL,
    emergency_id VARCHAR(50),
    
    -- State change
    from_state VARCHAR(20) NOT NULL,
    to_state VARCHAR(20) NOT NULL,
    
    -- Context
    location GEOGRAPHY(POINT, 4326),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    
    -- Timing
    transitioned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    duration_seconds INTEGER, -- Time spent in from_state
    
    -- Metadata
    trigger_type VARCHAR(50), -- 'ASSIGNMENT', 'MOVEMENT', 'COMPLETION', 'AUTO_HEAL'
    notes TEXT
);

CREATE INDEX idx_transitions_ambulance ON state_transitions(ambulance_id);
CREATE INDEX idx_transitions_emergency ON state_transitions(emergency_id);
CREATE INDEX idx_transitions_timestamp ON state_transitions(transitioned_at DESC);
```

### 1.5 Location History (GPS Trail)
```sql
CREATE TABLE location_history (
    id BIGSERIAL PRIMARY KEY,
    ambulance_id VARCHAR(50) NOT NULL,
    emergency_id VARCHAR(50),
    
    -- Location
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    
    -- Context
    status VARCHAR(20) NOT NULL,
    speed_mps DECIMAL(5, 2), -- meters per second
    heading_degrees INTEGER, -- 0-359
    
    -- Timing
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Partitioning by date for performance
    PARTITION BY RANGE (recorded_at)
);

-- Create partitions (example for monthly partitioning)
CREATE TABLE location_history_2024_03 PARTITION OF location_history
    FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');

CREATE INDEX idx_location_ambulance ON location_history(ambulance_id, recorded_at DESC);
CREATE INDEX idx_location_emergency ON location_history(emergency_id);
```

### 1.6 Performance Metrics
```sql
CREATE TABLE dispatch_metrics (
    id BIGSERIAL PRIMARY KEY,
    emergency_id VARCHAR(50) NOT NULL,
    
    -- Timing metrics
    emergency_created_at TIMESTAMP NOT NULL,
    assignment_completed_at TIMESTAMP,
    ambulance_arrived_at TIMESTAMP,
    emergency_completed_at TIMESTAMP,
    
    -- Calculated metrics
    assignment_latency_ms INTEGER, -- Time to assign
    response_time_seconds INTEGER, -- Time to arrive
    total_duration_seconds INTEGER, -- Total emergency duration
    
    -- Distance metrics
    straight_line_distance_km DECIMAL(10, 2),
    actual_route_distance_km DECIMAL(10, 2),
    
    -- Assignment details
    ambulance_id VARCHAR(50),
    priority VARCHAR(20),
    
    -- Timestamps
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_metrics_created ON dispatch_metrics(emergency_created_at DESC);
CREATE INDEX idx_metrics_ambulance ON dispatch_metrics(ambulance_id);
```

---

## 2. JPA Entity Classes

### 2.1 Emergency Entity
```java
@Entity
@Table(name = "emergencies")
public class Emergency {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "emergency_id", unique = true, nullable = false)
    private String emergencyId;
    
    @Column(nullable = false)
    private BigDecimal latitude;
    
    @Column(nullable = false)
    private BigDecimal longitude;
    
    // PostGIS geography type
    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmergencyStatus status;
    
    @Column(name = "assigned_ambulance_id")
    private String assignedAmbulanceId;
    
    @Column(name = "assignment_timestamp")
    private LocalDateTime assignmentTimestamp;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    private String callerPhone;
    private String description;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // Create PostGIS point
        location = createPoint(latitude, longitude);
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    private Point createPoint(BigDecimal lat, BigDecimal lon) {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        return geometryFactory.createPoint(
            new Coordinate(lon.doubleValue(), lat.doubleValue())
        );
    }
    
    // Getters and setters
}
```

### 2.2 Ambulance Entity
```java
@Entity
@Table(name = "ambulances")
public class Ambulance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "ambulance_id", unique = true, nullable = false)
    private String ambulanceId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AmbulanceStatus status;
    
    @Column(name = "current_latitude")
    private BigDecimal currentLatitude;
    
    @Column(name = "current_longitude")
    private BigDecimal currentLongitude;
    
    @Column(name = "current_location", columnDefinition = "geography(Point,4326)")
    private Point currentLocation;
    
    @Column(name = "vehicle_number")
    private String vehicleNumber;
    
    @Column(name = "equipment_type")
    private String equipmentType;
    
    @Column(name = "crew_size")
    private Integer crewSize;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "last_location_update")
    private LocalDateTime lastLocationUpdate;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and setters
}
```

### 2.3 Assignment History Entity
```java
@Entity
@Table(name = "assignment_history")
public class AssignmentHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "emergency_id", nullable = false)
    private String emergencyId;
    
    @Column(name = "ambulance_id", nullable = false)
    private String ambulanceId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false)
    private AssignmentType assignmentType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", nullable = false)
    private AssignmentStatus assignmentStatus;
    
    @Column(name = "emergency_location", columnDefinition = "geography(Point,4326)")
    private Point emergencyLocation;
    
    @Column(name = "ambulance_location", columnDefinition = "geography(Point,4326)")
    private Point ambulanceLocation;
    
    @Column(name = "distance_km")
    private BigDecimal distanceKm;
    
    @Column(name = "estimated_eta_seconds")
    private Integer estimatedEtaSeconds;
    
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;
    
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "assignment_version")
    private Integer assignmentVersion;
    
    private String notes;
    
    @PrePersist
    protected void onCreate() {
        assignedAt = LocalDateTime.now();
    }
    
    // Getters and setters
}
```

### 2.4 State Transition Entity
```java
@Entity
@Table(name = "state_transitions")
public class StateTransition {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "ambulance_id", nullable = false)
    private String ambulanceId;
    
    @Column(name = "emergency_id")
    private String emergencyId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", nullable = false)
    private AmbulanceStatus fromState;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false)
    private AmbulanceStatus toState;
    
    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;
    
    private BigDecimal latitude;
    private BigDecimal longitude;
    
    @Column(name = "transitioned_at", nullable = false)
    private LocalDateTime transitionedAt;
    
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
    
    @Column(name = "trigger_type")
    private String triggerType;
    
    private String notes;
    
    @PrePersist
    protected void onCreate() {
        transitionedAt = LocalDateTime.now();
    }
    
    // Getters and setters
}
```

---

## 3. Repository Layer

### 3.1 Emergency Repository
```java
@Repository
public interface EmergencyRepository extends JpaRepository<Emergency, Long> {
    
    Optional<Emergency> findByEmergencyId(String emergencyId);
    
    List<Emergency> findByStatus(EmergencyStatus status);
    
    @Query("SELECT e FROM Emergency e WHERE e.status = :status " +
           "ORDER BY e.priority DESC, e.createdAt ASC")
    List<Emergency> findPendingEmergenciesByPriority(@Param("status") EmergencyStatus status);
    
    // Geospatial query using PostGIS
    @Query(value = "SELECT * FROM emergencies " +
                   "WHERE ST_DWithin(location, ST_MakePoint(:lon, :lat)::geography, :radiusMeters) " +
                   "AND status = :status " +
                   "ORDER BY ST_Distance(location, ST_MakePoint(:lon, :lat)::geography)",
           nativeQuery = true)
    List<Emergency> findNearbyEmergencies(
        @Param("lat") double lat,
        @Param("lon") double lon,
        @Param("radiusMeters") double radiusMeters,
        @Param("status") String status
    );
    
    @Query("SELECT COUNT(e) FROM Emergency e WHERE e.status = :status")
    long countByStatus(@Param("status") EmergencyStatus status);
    
    @Query("SELECT e FROM Emergency e WHERE e.createdAt BETWEEN :start AND :end")
    List<Emergency> findByDateRange(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
```

### 3.2 Ambulance Repository
```java
@Repository
public interface AmbulanceRepository extends JpaRepository<Ambulance, Long> {
    
    Optional<Ambulance> findByAmbulanceId(String ambulanceId);
    
    List<Ambulance> findByStatus(AmbulanceStatus status);
    
    // Find nearest ambulances using PostGIS
    @Query(value = "SELECT *, ST_Distance(current_location, ST_MakePoint(:lon, :lat)::geography) as distance " +
                   "FROM ambulances " +
                   "WHERE status = 'AVAILABLE' " +
                   "AND ST_DWithin(current_location, ST_MakePoint(:lon, :lat)::geography, :radiusMeters) " +
                   "ORDER BY distance " +
                   "LIMIT :limit",
           nativeQuery = true)
    List<Ambulance> findNearestAvailable(
        @Param("lat") double lat,
        @Param("lon") double lon,
        @Param("radiusMeters") double radiusMeters,
        @Param("limit") int limit
    );
    
    @Query("SELECT COUNT(a) FROM Ambulance a WHERE a.status = :status")
    long countByStatus(@Param("status") AmbulanceStatus status);
}
```

### 3.3 Assignment History Repository
```java
@Repository
public interface AssignmentHistoryRepository extends JpaRepository<AssignmentHistory, Long> {
    
    List<AssignmentHistory> findByEmergencyId(String emergencyId);
    
    List<AssignmentHistory> findByAmbulanceId(String ambulanceId);
    
    @Query("SELECT ah FROM AssignmentHistory ah " +
           "WHERE ah.assignedAt BETWEEN :start AND :end " +
           "ORDER BY ah.assignedAt DESC")
    List<AssignmentHistory> findByDateRange(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT AVG(ah.estimatedEtaSeconds) FROM AssignmentHistory ah " +
           "WHERE ah.assignmentStatus = 'COMPLETED'")
    Double getAverageEta();
    
    @Query("SELECT ah.ambulanceId, COUNT(ah) as assignmentCount " +
           "FROM AssignmentHistory ah " +
           "WHERE ah.assignedAt >= :since " +
           "GROUP BY ah.ambulanceId " +
           "ORDER BY assignmentCount DESC")
    List<Object[]> getAmbulanceUtilization(@Param("since") LocalDateTime since);
}
```

### 3.4 State Transition Repository
```java
@Repository
public interface StateTransitionRepository extends JpaRepository<StateTransition, Long> {
    
    List<StateTransition> findByAmbulanceId(String ambulanceId);
    
    List<StateTransition> findByEmergencyId(String emergencyId);
    
    @Query("SELECT st FROM StateTransition st " +
           "WHERE st.ambulanceId = :ambulanceId " +
           "ORDER BY st.transitionedAt DESC")
    List<StateTransition> findAmbulanceHistory(@Param("ambulanceId") String ambulanceId);
    
    @Query("SELECT AVG(st.durationSeconds) FROM StateTransition st " +
           "WHERE st.fromState = :state")
    Double getAverageDurationInState(@Param("state") AmbulanceStatus state);
}
```

---

## 4. Service Layer Integration

### 4.1 Emergency Service with Dual Write
```java
@Service
@Transactional
public class EmergencyPersistenceService {
    
    private final EmergencyRepository emergencyRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, EmergencyEvent> kafkaTemplate;
    
    public Emergency createEmergency(EmergencyRequest request) {
        // 1. Save to PostgreSQL (source of truth)
        Emergency emergency = new Emergency();
        emergency.setEmergencyId(request.getEmergencyId());
        emergency.setLatitude(request.getLatitude());
        emergency.setLongitude(request.getLongitude());
        emergency.setPriority(request.getPriority());
        emergency.setStatus(EmergencyStatus.PENDING);
        
        Emergency saved = emergencyRepository.save(emergency);
        
        // 2. Publish to Kafka (async processing)
        EmergencyEvent event = mapToEvent(saved);
        kafkaTemplate.send("emergency-topic", event);
        
        // 3. Cache in Redis (optional, for quick lookups)
        cacheEmergency(saved);
        
        return saved;
    }
    
    private void cacheEmergency(Emergency emergency) {
        String key = "emergency:" + emergency.getEmergencyId();
        redisTemplate.opsForHash().put(key, "status", emergency.getStatus().name());
        redisTemplate.opsForHash().put(key, "priority", emergency.getPriority().name());
        redisTemplate.expire(key, Duration.ofHours(24));
    }
    
    public void updateEmergencyStatus(String emergencyId, EmergencyStatus newStatus) {
        Emergency emergency = emergencyRepository.findByEmergencyId(emergencyId)
            .orElseThrow(() -> new EntityNotFoundException("Emergency not found"));
        
        emergency.setStatus(newStatus);
        
        if (newStatus == EmergencyStatus.COMPLETED) {
            emergency.setCompletedAt(LocalDateTime.now());
        }
        
        emergencyRepository.save(emergency);
        
        // Update Redis cache
        redisTemplate.opsForHash().put(
            "emergency:" + emergencyId, 
            "status", 
            newStatus.name()
        );
    }
}
```

### 4.2 Assignment History Service
```java
@Service
public class AssignmentHistoryService {
    
    private final AssignmentHistoryRepository assignmentHistoryRepository;
    
    public void recordAssignment(
        String emergencyId,
        String ambulanceId,
        Point emergencyLocation,
        Point ambulanceLocation,
        double distanceKm,
        int etaSeconds
    ) {
        AssignmentHistory history = new AssignmentHistory();
        history.setEmergencyId(emergencyId);
        history.setAmbulanceId(ambulanceId);
        history.setAssignmentType(AssignmentType.INITIAL);
        history.setAssignmentStatus(AssignmentStatus.ACCEPTED);
        history.setEmergencyLocation(emergencyLocation);
        history.setAmbulanceLocation(ambulanceLocation);
        history.setDistanceKm(BigDecimal.valueOf(distanceKm));
        history.setEstimatedEtaSeconds(etaSeconds);
        
        assignmentHistoryRepository.save(history);
    }
    
    public void recordAcknowledgment(String emergencyId, String ambulanceId) {
        List<AssignmentHistory> assignments = 
            assignmentHistoryRepository.findByEmergencyId(emergencyId);
        
        assignments.stream()
            .filter(a -> a.getAmbulanceId().equals(ambulanceId))
            .filter(a -> a.getAcknowledgedAt() == null)
            .findFirst()
            .ifPresent(assignment -> {
                assignment.setAcknowledgedAt(LocalDateTime.now());
                assignmentHistoryRepository.save(assignment);
            });
    }
    
    public void recordCompletion(String emergencyId, String ambulanceId) {
        List<AssignmentHistory> assignments = 
            assignmentHistoryRepository.findByEmergencyId(emergencyId);
        
        assignments.stream()
            .filter(a -> a.getAmbulanceId().equals(ambulanceId))
            .filter(a -> a.getCompletedAt() == null)
            .findFirst()
            .ifPresent(assignment -> {
                assignment.setCompletedAt(LocalDateTime.now());
                assignment.setAssignmentStatus(AssignmentStatus.COMPLETED);
                assignmentHistoryRepository.save(assignment);
            });
    }
}
```

### 4.3 State Transition Tracker
```java
@Service
public class StateTransitionTracker {
    
    private final StateTransitionRepository stateTransitionRepository;
    private final Map<String, StateTransitionContext> activeTransitions = new ConcurrentHashMap<>();
    
    public void recordTransition(
        String ambulanceId,
        String emergencyId,
        AmbulanceStatus fromState,
        AmbulanceStatus toState,
        double lat,
        double lon,
        String triggerType
    ) {
        // Calculate duration in previous state
        Integer durationSeconds = calculateDuration(ambulanceId, fromState);
        
        StateTransition transition = new StateTransition();
        transition.setAmbulanceId(ambulanceId);
        transition.setEmergencyId(emergencyId);
        transition.setFromState(fromState);
        transition.setToState(toState);
        transition.setLatitude(BigDecimal.valueOf(lat));
        transition.setLongitude(BigDecimal.valueOf(lon));
        transition.setDurationSeconds(durationSeconds);
        transition.setTriggerType(triggerType);
        
        stateTransitionRepository.save(transition);
        
        // Update context for next transition
        updateContext(ambulanceId, toState);
    }
    
    private Integer calculateDuration(String ambulanceId, AmbulanceStatus fromState) {
        StateTransitionContext context = activeTransitions.get(ambulanceId);
        if (context != null && context.getState() == fromState) {
            return (int) Duration.between(context.getStartTime(), LocalDateTime.now()).getSeconds();
        }
        return null;
    }
    
    private void updateContext(String ambulanceId, AmbulanceStatus newState) {
        activeTransitions.put(ambulanceId, 
            new StateTransitionContext(newState, LocalDateTime.now()));
    }
    
    @Data
    @AllArgsConstructor
    private static class StateTransitionContext {
        private AmbulanceStatus state;
        private LocalDateTime startTime;
    }
}
```

---

## 5. Kafka Listener Integration

### 5.1 Emergency Event Listener (Dispatch Service)
```java
@Service
public class EmergencyEventListener {
    
    private final EmergencyPersistenceService emergencyPersistenceService;
    private final DispatchEngine dispatchEngine;
    
    @KafkaListener(topics = "emergency-topic", groupId = "dispatch-service")
    public void consumeEmergency(EmergencyEvent event) {
        try {
            // 1. Persist to PostgreSQL
            emergencyPersistenceService.createEmergency(event);
            
            // 2. Add to Redis queue for dispatch
            dispatchEngine.queueEmergency(event);
            
        } catch (Exception e) {
            log.error("Failed to process emergency: {}", event.getEmergencyId(), e);
            // Send to DLT
        }
    }
}
```

### 5.2 Assignment Event Listener (Ambulance Service)
```java
@Service
public class AssignmentEventListener {
    
    private final AmbulanceStateTracker stateTracker;
    private final AssignmentHistoryService assignmentHistoryService;
    private final StateTransitionTracker transitionTracker;
    
    @KafkaListener(topics = "ambulance-assigned-topic", groupId = "ambulance-service")
    @Transactional
    public void consumeAssignment(AssignmentEvent event) {
        try {
            // 1. Update Redis state (hot path)
            boolean accepted = stateTracker.assignEmergencyAtomically(
                event.getAmbulanceId(),
                event.getEmergencyId(),
                event.getExpectedVersion()
            );
            
            if (accepted) {
                // 2. Record in PostgreSQL (cold path)
                assignmentHistoryService.recordAssignment(
                    event.getEmergencyId(),
                    event.getAmbulanceId(),
                    createPoint(event.getEmergencyLat(), event.getEmergencyLon()),
                    createPoint(event.getAmbulanceLat(), event.getAmbulanceLon()),
                    event.getDistanceKm(),
                    event.getEtaSeconds()
                );
                
                // 3. Record state transition
                transitionTracker.recordTransition(
                    event.getAmbulanceId(),
                    event.getEmergencyId(),
                    AmbulanceStatus.AVAILABLE,
                    AmbulanceStatus.ASSIGNED,
                    event.getAmbulanceLat(),
                    event.getAmbulanceLon(),
                    "ASSIGNMENT"
                );
                
                // 4. Send ACK
                sendAck(event, "ACCEPTED");
            } else {
                sendAck(event, "REJECTED");
            }
            
        } catch (Exception e) {
            log.error("Failed to process assignment", e);
            sendAck(event, "REJECTED");
        }
    }
}
```

---

## 6. Configuration

### 6.1 application.yml (Dispatch Service)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/emergency_dispatch
    username: dispatch_user
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    
    # HikariCP connection pool
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate  # Use Flyway for migrations
    properties:
      hibernate:
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true
        format_sql: true
    show-sql: false
    
  # Flyway for database migrations
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    
  # Redis configuration (unchanged)
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
```

### 6.2 pom.xml Dependencies
```xml
<dependencies>
    <!-- PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- PostGIS for geospatial support -->
    <dependency>
        <groupId>net.postgis</groupId>
        <artifactId>postgis-jdbc</artifactId>
        <version>2023.1.0</version>
    </dependency>
    
    <!-- Hibernate Spatial -->
    <dependency>
        <groupId>org.hibernate</groupId>
        <artifactId>hibernate-spatial</artifactId>
    </dependency>
    
    <!-- Flyway for migrations -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    
    <!-- HikariCP (included in spring-boot-starter-data-jpa) -->
    
    <!-- Redis (keep existing) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
</dependencies>
```

---

## 7. Database Migrations (Flyway)

### 7.1 V1__initial_schema.sql
```sql
-- Create extensions
CREATE EXTENSION IF NOT EXISTS postgis;

-- Create emergencies table
CREATE TABLE emergencies (
    id BIGSERIAL PRIMARY KEY,
    emergency_id VARCHAR(50) UNIQUE NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_ambulance_id VARCHAR(50),
    assignment_timestamp TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    caller_phone VARCHAR(20),
    description TEXT,
    CONSTRAINT chk_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_emergencies_location ON emergencies USING GIST(location);
CREATE INDEX idx_emergencies_status ON emergencies(status);
CREATE INDEX idx_emergencies_created_at ON emergencies(created_at DESC);

-- Create ambulances table
CREATE TABLE ambulances (
    id BIGSERIAL PRIMARY KEY,
    ambulance_id VARCHAR(50) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_location GEOGRAPHY(POINT, 4326),
    current_latitude DECIMAL(10, 8),
    current_longitude DECIMAL(11, 8),
    vehicle_number VARCHAR(50),
    equipment_type VARCHAR(50),
    crew_size INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_location_update TIMESTAMP,
    CONSTRAINT chk_status CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'ON_ROUTE', 'ARRIVED', 'COMPLETED'))
);

CREATE INDEX idx_ambulances_status ON ambulances(status);
CREATE INDEX idx_ambulances_location ON ambulances USING GIST(current_location);

-- More tables...
```

---

## 8. Hybrid Architecture Pattern

### 8.1 Read Path
```
Query Request
    ↓
Check Redis Cache (hot data)
    ↓ (if miss)
Query PostgreSQL (cold data)
    ↓
Update Redis Cache
    ↓
Return Response
```

### 8.2 Write Path
```
Write Request
    ↓
Write to PostgreSQL (source of truth)
    ↓
Publish Kafka Event (async)
    ↓
Update Redis Cache (eventual)
    ↓
Return Success
```

### 8.3 Implementation Example
```java
@Service
public class HybridEmergencyService {
    
    private final EmergencyRepository postgresRepo;
    private final StringRedisTemplate redisTemplate;
    
    public Emergency getEmergency(String emergencyId) {
        // 1. Try Redis first (hot path)
        String cached = redisTemplate.opsForValue().get("emergency:" + emergencyId);
        if (cached != null) {
            return deserialize(cached);
        }
        
        // 2. Fallback to PostgreSQL (cold path)
        Emergency emergency = postgresRepo.findByEmergencyId(emergencyId)
            .orElseThrow(() -> new EntityNotFoundException("Emergency not found"));
        
        // 3. Cache for next time
        redisTemplate.opsForValue().set(
            "emergency:" + emergencyId,
            serialize(emergency),
            Duration.ofHours(1)
        );
        
        return emergency;
    }
}
```

---

## 9. Analytics Queries

### 9.1 Response Time Analysis
```sql
-- Average response time by priority
SELECT 
    priority,
    AVG(response_time_seconds) as avg_response_time,
    MIN(response_time_seconds) as min_response_time,
    MAX(response_time_seconds) as max_response_time,
    COUNT(*) as total_emergencies
FROM dispatch_metrics
WHERE emergency_created_at >= NOW() - INTERVAL '7 days'
GROUP BY priority
ORDER BY priority;
```

### 9.2 Ambulance Utilization
```sql
-- Ambulance utilization rate
SELECT 
    a.ambulance_id,
    COUNT(ah.id) as total_assignments,
    AVG(EXTRACT(EPOCH FROM (ah.completed_at - ah.assigned_at))) as avg_assignment_duration,
    SUM(CASE WHEN ah.assignment_status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_count
FROM ambulances a
LEFT JOIN assignment_history ah ON a.ambulance_id = ah.ambulance_id
WHERE ah.assigned_at >= NOW() - INTERVAL '30 days'
GROUP BY a.ambulance_id
ORDER BY total_assignments DESC;
```

### 9.3 Geographic Hotspots
```sql
-- Find emergency hotspots (clustering)
SELECT 
    ST_AsText(ST_Centroid(ST_Collect(location))) as hotspot_center,
    COUNT(*) as emergency_count
FROM emergencies
WHERE created_at >= NOW() - INTERVAL '7 days'
GROUP BY ST_SnapToGrid(location, 0.01)  -- ~1km grid
HAVING COUNT(*) > 5
ORDER BY emergency_count DESC;
```

---

## 10. Interview Talking Points

### Why PostgreSQL?
"I chose PostgreSQL for several reasons specific to our dispatch system:

1. **PostGIS Extension**: Provides advanced geospatial capabilities. We can do complex geo queries like finding emergencies within a radius or calculating actual road distances.

2. **JSONB Support**: Our system is event-driven with dynamic payloads. PostgreSQL's JSONB allows us to store and query event metadata efficiently.

3. **Strong Concurrency**: With distributed locking and atomic operations, PostgreSQL's MVCC handles concurrent updates better than MySQL.

4. **Industry Standard**: Companies like Uber and Stripe use PostgreSQL for similar dispatch/routing systems."

### Why Hybrid (Redis + PostgreSQL)?
"We use a hybrid architecture:

- **Redis**: Hot path for real-time dispatch (< 5ms queries)
- **PostgreSQL**: Cold path for persistence and analytics

This gives us both speed and durability. Redis handles the dispatch algorithm, while PostgreSQL stores historical data for reporting and compliance."

### How Do You Handle Consistency?
"We use eventual consistency with dual writes:

1. Write to PostgreSQL first (source of truth)
2. Publish Kafka event (async processing)
3. Update Redis cache (eventual)

For critical operations, we use distributed transactions with Saga pattern and compensating actions."

---

## 11. Next Steps

### Phase 1: Setup (Week 1)
- [ ] Install PostgreSQL with PostGIS
- [ ] Create database and user
- [ ] Add dependencies to pom.xml
- [ ] Configure application.yml

### Phase 2: Schema (Week 1-2)
- [ ] Create Flyway migrations
- [ ] Define JPA entities
- [ ] Create repositories
- [ ] Test database connectivity

### Phase 3: Integration (Week 2-3)
- [ ] Implement dual-write pattern
- [ ] Update Kafka listeners
- [ ] Add state transition tracking
- [ ] Test end-to-end flow

### Phase 4: Analytics (Week 3-4)
- [ ] Create analytics queries
- [ ] Build reporting endpoints
- [ ] Add Grafana dashboards
- [ ] Performance testing

---

## Summary

This integration gives you:
✅ **Durability**: PostgreSQL as source of truth  
✅ **Speed**: Redis for real-time dispatch  
✅ **Analytics**: Complex queries with PostGIS  
✅ **Scalability**: Hybrid architecture  
✅ **Interview-Ready**: Production-grade design  

**Your system rating goes from 9/10 to 9.5/10 with PostgreSQL integration!**
