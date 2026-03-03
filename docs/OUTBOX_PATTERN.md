# Outbox Pattern Implementation

## Problem with Current Dual Write

### Current Approach (UNSAFE)
```java
@Transactional
public Emergency createEmergency(EmergencyRequest request) {
    // 1. Save to PostgreSQL
    Emergency emergency = emergencyRepository.save(buildEmergency(request));
    
    // 2. Publish to Kafka ❌ NOT TRANSACTIONAL
    kafkaTemplate.send("emergency-topic", mapToEvent(emergency));
    
    return emergency;
}
```

**Issue**: If Kafka publish fails after DB commit → data in PostgreSQL but not in Kafka → inconsistent state.

---

## Solution: Transactional Outbox Pattern

### Architecture
```
1. Write to PostgreSQL + Outbox table (ATOMIC)
2. Background job polls Outbox
3. Publish to Kafka
4. Mark as published
```

### Benefits
- ✅ Atomic write (DB + Outbox in same transaction)
- ✅ Guaranteed delivery to Kafka
- ✅ No data loss
- ✅ Idempotent (can retry safely)

---

## Implementation

### 1. Outbox Table Schema

```sql
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,  -- 'EMERGENCY', 'ASSIGNMENT', etc.
    aggregate_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,      -- 'CREATED', 'ASSIGNED', etc.
    payload JSONB NOT NULL,
    
    -- Status tracking
    published BOOLEAN DEFAULT FALSE,
    published_at TIMESTAMP,
    
    -- Retry tracking
    retry_count INTEGER DEFAULT 0,
    last_retry_at TIMESTAMP,
    error_message TEXT,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Indexes
    CONSTRAINT chk_aggregate_type CHECK (aggregate_type IN ('EMERGENCY', 'ASSIGNMENT', 'STATE_TRANSITION')),
    CONSTRAINT chk_event_type CHECK (event_type IN ('CREATED', 'UPDATED', 'ASSIGNED', 'COMPLETED'))
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(published, created_at) WHERE published = FALSE;
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);
```

### 2. Outbox Entity

```java
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;
    
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Type(JsonBinaryType.class)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;
    
    @Column(name = "published")
    private Boolean published = false;
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // Getters and setters
}
```

### 3. Outbox Repository

```java
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    
    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false " +
           "AND (o.retryCount < 5 OR o.lastRetryAt < :retryThreshold) " +
           "ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnpublishedEvents(@Param("retryThreshold") LocalDateTime retryThreshold);
    
    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.published = false")
    long countUnpublished();
    
    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.published = true AND o.publishedAt < :threshold")
    int deleteOldPublishedEvents(@Param("threshold") LocalDateTime threshold);
}
```

### 4. Updated Service with Outbox

```java
@Service
@Transactional
public class EmergencyPersistenceService {
    
    private final EmergencyRepository emergencyRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    public Emergency createEmergency(EmergencyRequest request) {
        // 1. Save to PostgreSQL
        Emergency emergency = new Emergency();
        emergency.setEmergencyId(request.getEmergencyId());
        emergency.setLatitude(request.getLatitude());
        emergency.setLongitude(request.getLongitude());
        emergency.setPriority(request.getPriority());
        emergency.setStatus(EmergencyStatus.PENDING);
        
        Emergency saved = emergencyRepository.save(emergency);
        
        // 2. Write to Outbox (SAME TRANSACTION)
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("EMERGENCY");
        outboxEvent.setAggregateId(saved.getEmergencyId());
        outboxEvent.setEventType("CREATED");
        outboxEvent.setPayload(serializeToJson(mapToEvent(saved)));
        
        outboxRepository.save(outboxEvent);
        
        // Both writes are atomic - if either fails, both rollback
        return saved;
    }
    
    private String serializeToJson(EmergencyEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
```

### 5. Outbox Publisher (Background Job)

```java
@Service
@Slf4j
public class OutboxPublisher {
    
    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Scheduled(fixedDelay = 1000) // Every 1 second
    @Transactional
    public void publishPendingEvents() {
        LocalDateTime retryThreshold = LocalDateTime.now().minusMinutes(5);
        List<OutboxEvent> unpublished = outboxRepository.findUnpublishedEvents(retryThreshold);
        
        if (unpublished.isEmpty()) {
            return;
        }
        
        log.info("Publishing {} outbox events", unpublished.size());
        
        for (OutboxEvent event : unpublished) {
            try {
                publishEvent(event);
                markAsPublished(event);
            } catch (Exception e) {
                handlePublishFailure(event, e);
            }
        }
    }
    
    private void publishEvent(OutboxEvent event) {
        String topic = getTopicForAggregateType(event.getAggregateType());
        String key = event.getAggregateId();
        String payload = event.getPayload();
        
        kafkaTemplate.send(topic, key, payload).get(5, TimeUnit.SECONDS);
        log.info("Published event: {} {} to {}", event.getAggregateType(), event.getAggregateId(), topic);
    }
    
    @Transactional
    private void markAsPublished(OutboxEvent event) {
        event.setPublished(true);
        event.setPublishedAt(LocalDateTime.now());
        outboxRepository.save(event);
    }
    
    @Transactional
    private void handlePublishFailure(OutboxEvent event, Exception e) {
        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastRetryAt(LocalDateTime.now());
        event.setErrorMessage(e.getMessage());
        outboxRepository.save(event);
        
        log.error("Failed to publish event (retry {}): {} {}", 
            event.getRetryCount(), event.getAggregateType(), event.getAggregateId(), e);
        
        if (event.getRetryCount() >= 5) {
            log.error("Event exceeded max retries, sending to DLT: {}", event.getId());
            // Send to Dead Letter Topic or alert
        }
    }
    
    private String getTopicForAggregateType(String aggregateType) {
        return switch (aggregateType) {
            case "EMERGENCY" -> "emergency-topic";
            case "ASSIGNMENT" -> "ambulance-assigned-topic";
            case "STATE_TRANSITION" -> "ambulance-state-topic";
            default -> throw new IllegalArgumentException("Unknown aggregate type: " + aggregateType);
        };
    }
    
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void cleanupOldEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int deleted = outboxRepository.deleteOldPublishedEvents(threshold);
        log.info("Cleaned up {} old outbox events", deleted);
    }
}
```

### 6. Monitoring

```java
@Component
public class OutboxMetrics {
    
    private final OutboxEventRepository outboxRepository;
    private final MeterRegistry meterRegistry;
    
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void recordMetrics() {
        long unpublished = outboxRepository.countUnpublished();
        meterRegistry.gauge("outbox.events.unpublished", unpublished);
    }
}
```

---

## Comparison: Before vs After

### Before (Unsafe Dual Write)
```
Emergency Service
    ↓
PostgreSQL.save() ✅
    ↓
Kafka.send() ❌ FAILS
    ↓
Result: Data in DB, not in Kafka → INCONSISTENT
```

### After (Outbox Pattern)
```
Emergency Service
    ↓
PostgreSQL.save() + Outbox.save() ✅ (ATOMIC)
    ↓
Background Job
    ↓
Kafka.send() ❌ FAILS
    ↓
Retry automatically
    ↓
Eventually published → CONSISTENT
```

---

## Configuration

```yaml
# application.yml
outbox:
  publisher:
    enabled: true
    poll-interval-ms: 1000
    batch-size: 100
    max-retries: 5
    retry-delay-minutes: 5
    cleanup-days: 7
```

---

## Testing

```java
@SpringBootTest
@Transactional
class OutboxPatternTest {
    
    @Autowired
    private EmergencyPersistenceService emergencyService;
    
    @Autowired
    private OutboxEventRepository outboxRepository;
    
    @Test
    void shouldWriteToOutboxAtomically() {
        // Given
        EmergencyRequest request = new EmergencyRequest(
            "EMG-001", 18.5204, 73.8567, Priority.HIGH
        );
        
        // When
        Emergency emergency = emergencyService.createEmergency(request);
        
        // Then
        assertThat(emergency.getId()).isNotNull();
        
        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAggregateId()).isEqualTo("EMG-001");
        assertThat(events.get(0).getPublished()).isFalse();
    }
    
    @Test
    void shouldPublishAndMarkAsPublished() {
        // Given
        OutboxEvent event = createTestOutboxEvent();
        outboxRepository.save(event);
        
        // When
        outboxPublisher.publishPendingEvents();
        
        // Then
        OutboxEvent published = outboxRepository.findById(event.getId()).get();
        assertThat(published.getPublished()).isTrue();
        assertThat(published.getPublishedAt()).isNotNull();
    }
}
```

---

## Metrics to Monitor

```
outbox.events.unpublished          # Should be near 0
outbox.events.published.total      # Increasing
outbox.events.failed.total         # Should be low
outbox.publisher.latency           # < 2 seconds
outbox.events.retry.count          # Track retries
```

---

## Alerts

```yaml
- alert: OutboxBacklog
  expr: outbox_events_unpublished > 100
  for: 5m
  annotations:
    summary: "Outbox has {{ $value }} unpublished events"

- alert: OutboxPublisherDown
  expr: rate(outbox_events_published_total[5m]) == 0
  for: 10m
  annotations:
    summary: "Outbox publisher not publishing events"
```

---

## Interview Answer

**Q: How do you ensure consistency between PostgreSQL and Kafka?**

**A**: "We use the Transactional Outbox pattern. Instead of dual-write (DB then Kafka), we:

1. Write to PostgreSQL and an outbox table in the same transaction (atomic)
2. A background job polls the outbox every second
3. Publishes events to Kafka
4. Marks as published

This guarantees at-least-once delivery. If Kafka is down, events stay in outbox and retry automatically. We monitor outbox depth and alert if it grows beyond 100 events."

---

## Summary

✅ **Atomic writes** (DB + Outbox)  
✅ **Guaranteed delivery** (retry until success)  
✅ **No data loss** (events persisted)  
✅ **Idempotent** (safe to retry)  
✅ **Monitorable** (outbox depth metric)  
✅ **Production-grade** (used by Uber, Netflix)  

**This is the correct way to do dual writes in distributed systems.**
