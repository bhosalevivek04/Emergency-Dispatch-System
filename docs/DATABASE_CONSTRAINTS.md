# Database Constraints & Referential Integrity

## Problem: Missing Foreign Keys

Current schema has NO foreign key constraints, allowing:
- Invalid ambulance IDs in assignment_history
- Orphaned records
- Data integrity issues

---

## Solution: Proper Constraints

### 1. Updated Schema with Foreign Keys

```sql
-- ============================================
-- MASTER TABLES (No dependencies)
-- ============================================

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
    
    CONSTRAINT chk_ambulance_status CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'ON_ROUTE', 'ARRIVED', 'COMPLETED'))
);

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
    
    CONSTRAINT chk_emergency_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_emergency_status CHECK (status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    
    -- Foreign key to ambulances
    CONSTRAINT fk_emergency_ambulance 
        FOREIGN KEY (assigned_ambulance_id) 
        REFERENCES ambulances(ambulance_id)
        ON DELETE SET NULL  -- If ambulance deleted, set to NULL
        ON UPDATE CASCADE   -- If ambulance_id changes, update here
);

-- ============================================
-- DEPENDENT TABLES (With Foreign Keys)
-- ============================================

CREATE TABLE assignment_history (
    id BIGSERIAL PRIMARY KEY,
    emergency_id VARCHAR(50) NOT NULL,
    ambulance_id VARCHAR(50) NOT NULL,
    assignment_type VARCHAR(20) NOT NULL,
    assignment_status VARCHAR(20) NOT NULL,
    emergency_location GEOGRAPHY(POINT, 4326),
    ambulance_location GEOGRAPHY(POINT, 4326),
    distance_km DECIMAL(10, 2),
    estimated_eta_seconds INTEGER,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMP,
    completed_at TIMESTAMP,
    assignment_version INTEGER,
    notes TEXT,
    
    CONSTRAINT chk_assignment_type CHECK (assignment_type IN ('INITIAL', 'REASSIGNMENT')),
    CONSTRAINT chk_assignment_status CHECK (assignment_status IN ('ACCEPTED', 'REJECTED', 'TIMEOUT', 'COMPLETED')),
    
    -- Foreign keys
    CONSTRAINT fk_assignment_emergency 
        FOREIGN KEY (emergency_id) 
        REFERENCES emergencies(emergency_id)
        ON DELETE CASCADE   -- If emergency deleted, delete assignments
        ON UPDATE CASCADE,
    
    CONSTRAINT fk_assignment_ambulance 
        FOREIGN KEY (ambulance_id) 
        REFERENCES ambulances(ambulance_id)
        ON DELETE CASCADE   -- If ambulance deleted, delete assignments
        ON UPDATE CASCADE
);

CREATE TABLE state_transitions (
    id BIGSERIAL PRIMARY KEY,
    ambulance_id VARCHAR(50) NOT NULL,
    emergency_id VARCHAR(50),
    from_state VARCHAR(20) NOT NULL,
    to_state VARCHAR(20) NOT NULL,
    location GEOGRAPHY(POINT, 4326),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    transitioned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    duration_seconds INTEGER,
    trigger_type VARCHAR(50),
    notes TEXT,
    
    -- Foreign keys
    CONSTRAINT fk_transition_ambulance 
        FOREIGN KEY (ambulance_id) 
        REFERENCES ambulances(ambulance_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    
    CONSTRAINT fk_transition_emergency 
        FOREIGN KEY (emergency_id) 
        REFERENCES emergencies(emergency_id)
        ON DELETE SET NULL  -- Keep transition log even if emergency deleted
        ON UPDATE CASCADE
);

CREATE TABLE location_history (
    id BIGSERIAL PRIMARY KEY,
    ambulance_id VARCHAR(50) NOT NULL,
    emergency_id VARCHAR(50),
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    status VARCHAR(20) NOT NULL,
    speed_mps DECIMAL(5, 2),
    heading_degrees INTEGER,
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Foreign keys
    CONSTRAINT fk_location_ambulance 
        FOREIGN KEY (ambulance_id) 
        REFERENCES ambulances(ambulance_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    
    CONSTRAINT fk_location_emergency 
        FOREIGN KEY (emergency_id) 
        REFERENCES emergencies(emergency_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) PARTITION BY RANGE (recorded_at);

CREATE TABLE dispatch_metrics (
    id BIGSERIAL PRIMARY KEY,
    emergency_id VARCHAR(50) NOT NULL,
    emergency_created_at TIMESTAMP NOT NULL,
    assignment_completed_at TIMESTAMP,
    ambulance_arrived_at TIMESTAMP,
    emergency_completed_at TIMESTAMP,
    assignment_latency_ms INTEGER,
    response_time_seconds INTEGER,
    total_duration_seconds INTEGER,
    straight_line_distance_km DECIMAL(10, 2),
    actual_route_distance_km DECIMAL(10, 2),
    ambulance_id VARCHAR(50),
    priority VARCHAR(20),
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Foreign keys
    CONSTRAINT fk_metrics_emergency 
        FOREIGN KEY (emergency_id) 
        REFERENCES emergencies(emergency_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    
    CONSTRAINT fk_metrics_ambulance 
        FOREIGN KEY (ambulance_id) 
        REFERENCES ambulances(ambulance_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
);
```

---

## 2. Indexes for Foreign Keys

```sql
-- Foreign key indexes (CRITICAL for performance)
CREATE INDEX idx_emergencies_ambulance_fk ON emergencies(assigned_ambulance_id);
CREATE INDEX idx_assignment_emergency_fk ON assignment_history(emergency_id);
CREATE INDEX idx_assignment_ambulance_fk ON assignment_history(ambulance_id);
CREATE INDEX idx_transition_ambulance_fk ON state_transitions(ambulance_id);
CREATE INDEX idx_transition_emergency_fk ON state_transitions(emergency_id);
CREATE INDEX idx_location_ambulance_fk ON location_history(ambulance_id);
CREATE INDEX idx_location_emergency_fk ON location_history(emergency_id);
CREATE INDEX idx_metrics_emergency_fk ON dispatch_metrics(emergency_id);
CREATE INDEX idx_metrics_ambulance_fk ON dispatch_metrics(ambulance_id);
```

---

## 3. Cascade Behavior Explained

### ON DELETE CASCADE
```sql
-- If ambulance deleted → delete all related records
CONSTRAINT fk_assignment_ambulance 
    FOREIGN KEY (ambulance_id) 
    REFERENCES ambulances(ambulance_id)
    ON DELETE CASCADE
```

**Use when**: Child records meaningless without parent

### ON DELETE SET NULL
```sql
-- If emergency deleted → keep transition log, set emergency_id to NULL
CONSTRAINT fk_transition_emergency 
    FOREIGN KEY (emergency_id) 
    REFERENCES emergencies(emergency_id)
    ON DELETE SET NULL
```

**Use when**: Child records still valuable for audit

### ON UPDATE CASCADE
```sql
-- If ambulance_id changes → update all references automatically
ON UPDATE CASCADE
```

**Use when**: Primary key might change (though rare)

---

## 4. JPA Entity Relationships

### Emergency Entity
```java
@Entity
@Table(name = "emergencies")
public class Emergency {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "emergency_id", unique = true, nullable = false)
    private String emergencyId;
    
    // ... other fields
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_ambulance_id", referencedColumnName = "ambulance_id")
    private Ambulance assignedAmbulance;
    
    @OneToMany(mappedBy = "emergency", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssignmentHistory> assignmentHistory = new ArrayList<>();
    
    @OneToMany(mappedBy = "emergency", cascade = CascadeType.ALL)
    private List<StateTransition> stateTransitions = new ArrayList<>();
}
```

### Ambulance Entity
```java
@Entity
@Table(name = "ambulances")
public class Ambulance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "ambulance_id", unique = true, nullable = false)
    private String ambulanceId;
    
    // ... other fields
    
    @OneToMany(mappedBy = "ambulance", cascade = CascadeType.ALL)
    private List<AssignmentHistory> assignmentHistory = new ArrayList<>();
    
    @OneToMany(mappedBy = "ambulance", cascade = CascadeType.ALL)
    private List<StateTransition> stateTransitions = new ArrayList<>();
    
    @OneToMany(mappedBy = "ambulance", cascade = CascadeType.ALL)
    private List<LocationHistory> locationHistory = new ArrayList<>();
}
```

### AssignmentHistory Entity
```java
@Entity
@Table(name = "assignment_history")
public class AssignmentHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emergency_id", referencedColumnName = "emergency_id", nullable = false)
    private Emergency emergency;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ambulance_id", referencedColumnName = "ambulance_id", nullable = false)
    private Ambulance ambulance;
    
    // ... other fields
}
```

---

## 5. Constraint Violation Handling

```java
@Service
public class EmergencyPersistenceService {
    
    public Emergency assignAmbulance(String emergencyId, String ambulanceId) {
        try {
            Emergency emergency = emergencyRepository.findByEmergencyId(emergencyId)
                .orElseThrow(() -> new EntityNotFoundException("Emergency not found"));
            
            Ambulance ambulance = ambulanceRepository.findByAmbulanceId(ambulanceId)
                .orElseThrow(() -> new EntityNotFoundException("Ambulance not found"));
            
            emergency.setAssignedAmbulance(ambulance);
            emergency.setStatus(EmergencyStatus.ASSIGNED);
            
            return emergencyRepository.save(emergency);
            
        } catch (DataIntegrityViolationException e) {
            // Foreign key constraint violated
            log.error("Failed to assign ambulance: constraint violation", e);
            throw new InvalidAssignmentException("Invalid ambulance or emergency ID");
        }
    }
}
```

---

## 6. Testing Constraints

```java
@SpringBootTest
@Transactional
class DatabaseConstraintsTest {
    
    @Autowired
    private EmergencyRepository emergencyRepository;
    
    @Autowired
    private AmbulanceRepository ambulanceRepository;
    
    @Autowired
    private AssignmentHistoryRepository assignmentHistoryRepository;
    
    @Test
    void shouldEnforceForeignKeyConstraint() {
        // Given: Emergency with invalid ambulance ID
        Emergency emergency = new Emergency();
        emergency.setEmergencyId("EMG-001");
        emergency.setAssignedAmbulanceId("INVALID-ID");  // Doesn't exist
        
        // When/Then: Should throw constraint violation
        assertThatThrownBy(() -> emergencyRepository.save(emergency))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
    
    @Test
    void shouldCascadeDeleteAssignments() {
        // Given: Ambulance with assignments
        Ambulance ambulance = createTestAmbulance();
        ambulanceRepository.save(ambulance);
        
        Emergency emergency = createTestEmergency();
        emergencyRepository.save(emergency);
        
        AssignmentHistory assignment = new AssignmentHistory();
        assignment.setEmergency(emergency);
        assignment.setAmbulance(ambulance);
        assignmentHistoryRepository.save(assignment);
        
        // When: Delete ambulance
        ambulanceRepository.delete(ambulance);
        
        // Then: Assignment should be deleted (CASCADE)
        assertThat(assignmentHistoryRepository.findAll()).isEmpty();
    }
    
    @Test
    void shouldSetNullOnEmergencyDelete() {
        // Given: State transition linked to emergency
        Emergency emergency = createTestEmergency();
        emergencyRepository.save(emergency);
        
        StateTransition transition = new StateTransition();
        transition.setEmergency(emergency);
        stateTransitionRepository.save(transition);
        
        // When: Delete emergency
        emergencyRepository.delete(emergency);
        
        // Then: Transition still exists, emergency_id is NULL
        StateTransition found = stateTransitionRepository.findById(transition.getId()).get();
        assertThat(found.getEmergency()).isNull();
    }
}
```

---

## 7. Migration Strategy

### Flyway Migration: V2__add_foreign_keys.sql
```sql
-- Step 1: Clean up invalid data
DELETE FROM assignment_history 
WHERE emergency_id NOT IN (SELECT emergency_id FROM emergencies);

DELETE FROM assignment_history 
WHERE ambulance_id NOT IN (SELECT ambulance_id FROM ambulances);

-- Step 2: Add foreign keys
ALTER TABLE emergencies
ADD CONSTRAINT fk_emergency_ambulance 
    FOREIGN KEY (assigned_ambulance_id) 
    REFERENCES ambulances(ambulance_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE assignment_history
ADD CONSTRAINT fk_assignment_emergency 
    FOREIGN KEY (emergency_id) 
    REFERENCES emergencies(emergency_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

ALTER TABLE assignment_history
ADD CONSTRAINT fk_assignment_ambulance 
    FOREIGN KEY (ambulance_id) 
    REFERENCES ambulances(ambulance_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- Step 3: Add indexes
CREATE INDEX idx_emergencies_ambulance_fk ON emergencies(assigned_ambulance_id);
CREATE INDEX idx_assignment_emergency_fk ON assignment_history(emergency_id);
CREATE INDEX idx_assignment_ambulance_fk ON assignment_history(ambulance_id);
```

---

## 8. Performance Impact

### Without Foreign Keys
- ✅ Faster writes (no constraint checking)
- ❌ Data integrity issues
- ❌ Orphaned records
- ❌ Invalid references

### With Foreign Keys
- ⚠️ Slightly slower writes (~5-10% overhead)
- ✅ Data integrity guaranteed
- ✅ No orphaned records
- ✅ Referential integrity
- ✅ Cascade operations automatic

**Verdict**: Worth the small performance cost for data integrity

---

## 9. Monitoring

```sql
-- Check for constraint violations (before adding FKs)
SELECT 'assignment_history' as table_name, COUNT(*) as invalid_count
FROM assignment_history ah
WHERE NOT EXISTS (
    SELECT 1 FROM emergencies e WHERE e.emergency_id = ah.emergency_id
)
UNION ALL
SELECT 'assignment_history', COUNT(*)
FROM assignment_history ah
WHERE NOT EXISTS (
    SELECT 1 FROM ambulances a WHERE a.ambulance_id = ah.ambulance_id
);

-- Check constraint usage
SELECT 
    tc.constraint_name,
    tc.table_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
AND tc.table_schema = 'public';
```

---

## Summary

✅ **Foreign keys added** to all dependent tables  
✅ **Cascade behavior** defined appropriately  
✅ **Indexes created** for FK columns  
✅ **JPA relationships** mapped correctly  
✅ **Migration strategy** for existing data  
✅ **Tests** for constraint enforcement  

**This is production-grade referential integrity.**
