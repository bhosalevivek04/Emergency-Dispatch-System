# Optimistic Locking in PostgreSQL

## Problem: Concurrent Updates Without Version Control

Current entities have NO optimistic locking:
```java
@Entity
public class Emergency {
    @Id
    private Long id;
    private String status;
    // No @Version field ❌
}
```

**Issue**: Two threads can update same record simultaneously → lost updates

---

## Solution: Add @Version to All Entities

### 1. Updated Schema

```sql
-- Add version column to all tables
ALTER TABLE emergencies ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE ambulances ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE assignment_history ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE state_transitions ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

-- Create indexes for version checks
CREATE INDEX idx_emergencies_version ON emergencies(id, version);
CREATE INDEX idx_ambulances_version ON ambulances(id, version);
```

### 2. Updated Entities

#### Emergency Entity
```java
@Entity
@Table(name = "emergencies")
public class Emergency {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "emergency_id", unique = true, nullable = false)
    private String emergencyId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmergencyStatus status;
    
    // Optimistic locking
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    // ... other fields
}
```

#### Ambulance Entity
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
    
    // Optimistic locking
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    // ... other fields
}
```

#### AssignmentHistory Entity
```java
@Entity
@Table(name = "assignment_history")
public class AssignmentHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emergency_id", nullable = false)
    private Emergency emergency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", nullable = false)
    private AssignmentStatus assignmentStatus;
    
    // Optimistic locking
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    // ... other fields
}
```

---

## 3. How It Works

### Without @Version (UNSAFE)
```java
// Thread 1
Emergency e1 = repo.findById(1);  // status = PENDING
e1.setStatus(ASSIGNED);
repo.save(e1);  // ✅ Saved

// Thread 2 (concurrent)
Emergency e2 = repo.findById(1);  // status = PENDING (stale read)
e2.setStatus(CANCELLED);
repo.save(e2);  // ✅ Saved (overwrites Thread 1's update) ❌
```

**Result**: Thread 1's update lost (ASSIGNED → CANCELLED)

### With @Version (SAFE)
```java
// Thread 1
Emergency e1 = repo.findById(1);  // version = 0, status = PENDING
e1.setStatus(ASSIGNED);
repo.save(e1);  // ✅ Saved, version = 1

// Thread 2 (concurrent)
Emergency e2 = repo.findById(1);  // version = 0, status = PENDING (stale)
e2.setStatus(CANCELLED);
repo.save(e2);  // ❌ OptimisticLockException (version mismatch)
```

**Result**: Thread 2 fails, must retry with fresh data

---

## 4. Service Layer Handling

### Basic Update with Retry
```java
@Service
public class EmergencyService {
    
    private final EmergencyRepository emergencyRepository;
    
    @Transactional
    public Emergency updateStatus(String emergencyId, EmergencyStatus newStatus) {
        return updateWithRetry(emergencyId, newStatus, 3);
    }
    
    private Emergency updateWithRetry(String emergencyId, EmergencyStatus newStatus, int maxRetries) {
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                Emergency emergency = emergencyRepository.findByEmergencyId(emergencyId)
                    .orElseThrow(() -> new EntityNotFoundException("Emergency not found"));
                
                emergency.setStatus(newStatus);
                emergency.setUpdatedAt(LocalDateTime.now());
                
                return emergencyRepository.save(emergency);
                
            } catch (OptimisticLockException e) {
                attempt++;
                log.warn("Optimistic lock conflict on emergency {}, retry {}/{}", 
                    emergencyId, attempt, maxRetries);
                
                if (attempt >= maxRetries) {
                    throw new ConcurrentUpdateException(
                        "Failed to update emergency after " + maxRetries + " retries", e);
                }
                
                // Brief pause before retry
                try {
                    Thread.sleep(50 * attempt);  // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
        
        throw new ConcurrentUpdateException("Should not reach here");
    }
}
```

### Advanced: Conditional Update
```java
@Service
public class AmbulanceService {
    
    @Transactional
    public boolean assignEmergency(String ambulanceId, String emergencyId) {
        try {
            Ambulance ambulance = ambulanceRepository.findByAmbulanceId(ambulanceId)
                .orElseThrow(() -> new EntityNotFoundException("Ambulance not found"));
            
            // Check current state
            if (ambulance.getStatus() != AmbulanceStatus.AVAILABLE) {
                log.warn("Ambulance {} not available, current status: {}", 
                    ambulanceId, ambulance.getStatus());
                return false;
            }
            
            // Update state
            ambulance.setStatus(AmbulanceStatus.ASSIGNED);
            ambulance.setActiveEmergencyId(emergencyId);
            ambulance.setUpdatedAt(LocalDateTime.now());
            
            ambulanceRepository.save(ambulance);
            return true;
            
        } catch (OptimisticLockException e) {
            log.warn("Concurrent assignment attempt on ambulance {}", ambulanceId);
            return false;  // Another thread won the race
        }
    }
}
```

---

## 5. REST API Error Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException e) {
        ErrorResponse error = new ErrorResponse(
            "CONCURRENT_UPDATE",
            "The resource was modified by another request. Please retry.",
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    @ExceptionHandler(ConcurrentUpdateException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(ConcurrentUpdateException e) {
        ErrorResponse error = new ErrorResponse(
            "UPDATE_FAILED",
            "Failed to update resource after multiple retries due to concurrent modifications.",
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}
```

---

## 6. Testing Optimistic Locking

```java
@SpringBootTest
@Transactional
class OptimisticLockingTest {
    
    @Autowired
    private EmergencyRepository emergencyRepository;
    
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    @Test
    void shouldThrowOptimisticLockException() throws Exception {
        // Given: Emergency in database
        Emergency emergency = new Emergency();
        emergency.setEmergencyId("EMG-001");
        emergency.setStatus(EmergencyStatus.PENDING);
        emergency = emergencyRepository.save(emergency);
        
        Long emergencyId = emergency.getId();
        
        // When: Two threads update simultaneously
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Exception> thread1Exception = new AtomicReference<>();
        AtomicReference<Exception> thread2Exception = new AtomicReference<>();
        
        // Thread 1
        new Thread(() -> {
            try {
                TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                txTemplate.execute(status -> {
                    Emergency e = emergencyRepository.findById(emergencyId).get();
                    e.setStatus(EmergencyStatus.ASSIGNED);
                    emergencyRepository.save(e);
                    return null;
                });
            } catch (Exception e) {
                thread1Exception.set(e);
            } finally {
                latch.countDown();
            }
        }).start();
        
        // Thread 2
        new Thread(() -> {
            try {
                TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                txTemplate.execute(status -> {
                    Emergency e = emergencyRepository.findById(emergencyId).get();
                    e.setStatus(EmergencyStatus.CANCELLED);
                    emergencyRepository.save(e);
                    return null;
                });
            } catch (Exception e) {
                thread2Exception.set(e);
            } finally {
                latch.countDown();
            }
        }).start();
        
        latch.await(5, TimeUnit.SECONDS);
        
        // Then: One thread should succeed, one should fail
        boolean hasOptimisticLockException = 
            (thread1Exception.get() instanceof OptimisticLockException) ||
            (thread2Exception.get() instanceof OptimisticLockException);
        
        assertThat(hasOptimisticLockException).isTrue();
    }
    
    @Test
    void shouldRetryAndSucceed() {
        // Given
        Emergency emergency = new Emergency();
        emergency.setEmergencyId("EMG-002");
        emergency.setStatus(EmergencyStatus.PENDING);
        emergency = emergencyRepository.save(emergency);
        
        // When: Update with retry logic
        Emergency updated = emergencyService.updateStatus("EMG-002", EmergencyStatus.ASSIGNED);
        
        // Then
        assertThat(updated.getStatus()).isEqualTo(EmergencyStatus.ASSIGNED);
        assertThat(updated.getVersion()).isGreaterThan(0L);
    }
}
```

---

## 7. Metrics & Monitoring

```java
@Component
public class OptimisticLockMetrics {
    
    private final MeterRegistry meterRegistry;
    
    @EventListener
    public void onOptimisticLockException(OptimisticLockException e) {
        meterRegistry.counter("optimistic_lock.conflicts.total",
            "entity", extractEntityName(e)
        ).increment();
    }
    
    private String extractEntityName(OptimisticLockException e) {
        // Extract entity name from exception
        return e.getMessage().contains("Emergency") ? "Emergency" : "Unknown";
    }
}
```

### Prometheus Metrics
```
optimistic_lock_conflicts_total{entity="Emergency"} 5
optimistic_lock_conflicts_total{entity="Ambulance"} 12
```

### Alerts
```yaml
- alert: HighOptimisticLockConflicts
  expr: rate(optimistic_lock_conflicts_total[5m]) > 10
  for: 5m
  annotations:
    summary: "High rate of optimistic lock conflicts"
```

---

## 8. Comparison: Redis vs PostgreSQL Locking

### Redis (Current)
```java
// Lua script for atomic update
String script = """
    local status = redis.call('GET', KEYS[1])
    if status ~= 'AVAILABLE' then return -1 end
    redis.call('SET', KEYS[1], 'ASSIGNED')
    return 1
""";
```

**Pros**: Atomic, fast (< 1ms)  
**Cons**: No version tracking, harder to debug

### PostgreSQL (With @Version)
```java
// JPA optimistic locking
Ambulance ambulance = repo.findById(id);
ambulance.setStatus(ASSIGNED);
repo.save(ambulance);  // Throws OptimisticLockException if version mismatch
```

**Pros**: Version tracking, easier debugging, standard pattern  
**Cons**: Slightly slower (~5ms)

### Hybrid Approach (RECOMMENDED)
- **Redis**: For real-time dispatch (speed critical)
- **PostgreSQL**: For historical updates (consistency critical)
- **Both**: Use version numbers for audit trail

---

## 9. Migration

### Flyway: V3__add_version_columns.sql
```sql
-- Add version columns with default 0
ALTER TABLE emergencies ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE ambulances ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE assignment_history ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE state_transitions ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

-- Update existing rows to version 0
UPDATE emergencies SET version = 0 WHERE version IS NULL;
UPDATE ambulances SET version = 0 WHERE version IS NULL;
UPDATE assignment_history SET version = 0 WHERE version IS NULL;
UPDATE state_transitions SET version = 0 WHERE version IS NULL;

-- Create indexes
CREATE INDEX idx_emergencies_version ON emergencies(id, version);
CREATE INDEX idx_ambulances_version ON ambulances(id, version);
```

---

## 10. Interview Answer

**Q: How do you handle concurrent updates in your system?**

**A**: "We use optimistic locking with JPA's @Version annotation. Each entity has a version column that increments on every update. When two threads try to update the same record:

1. Both read the record with version N
2. First thread saves successfully, version becomes N+1
3. Second thread tries to save with version N
4. JPA detects version mismatch and throws OptimisticLockException
5. We catch it and retry with fresh data

For critical real-time operations like dispatch, we use Redis Lua scripts for atomic updates. For historical data in PostgreSQL, we use @Version for consistency. This hybrid approach gives us both speed and safety."

---

## Summary

✅ **@Version added** to all entities  
✅ **Retry logic** for conflict resolution  
✅ **Error handling** for REST API  
✅ **Tests** for concurrent updates  
✅ **Metrics** for monitoring conflicts  
✅ **Migration** for existing data  

**This is production-grade concurrency control.**
