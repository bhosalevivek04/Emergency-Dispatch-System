# Redis + PostgreSQL Hybrid Architecture

## Quick Reference Guide

### When to Use Redis vs PostgreSQL

| Use Case | Use Redis | Use PostgreSQL | Reason |
|----------|-----------|----------------|--------|
| Dispatch algorithm | ✅ | ❌ | Need < 5ms response |
| Ambulance state | ✅ | ❌ | Real-time updates |
| Geospatial search | ✅ | ❌ | Redis GEO is faster |
| Distributed locks | ✅ | ❌ | TTL-based expiry |
| Idempotency cache | ✅ | ❌ | Temporary data |
| Emergency history | ❌ | ✅ | Permanent records |
| Assignment audit | ❌ | ✅ | Compliance/reporting |
| Analytics queries | ❌ | ✅ | Complex aggregations |
| State transitions | ❌ | ✅ | Audit trail |
| Location history | ❌ | ✅ | GPS trail storage |

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        API Gateway                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     Emergency Service                        │
│  ┌──────────────┐         ┌──────────────┐                 │
│  │ PostgreSQL   │◄────────│  Controller  │                 │
│  │ (Write)      │         └──────┬───────┘                 │
│  └──────────────┘                │                          │
│                                   ▼                          │
│                          ┌──────────────┐                   │
│                          │    Kafka     │                   │
│                          │  Producer    │                   │
│                          └──────────────┘                   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  Kafka Topics    │
                    └──────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     Dispatch Service                         │
│  ┌──────────────┐         ┌──────────────┐                 │
│  │    Redis     │◄────────│   Dispatch   │                 │
│  │  (Hot Path)  │         │   Engine     │                 │
│  │              │         └──────┬───────┘                 │
│  │ • GEO Index  │                │                          │
│  │ • Queues     │                ▼                          │
│  │ • Locks      │         ┌──────────────┐                 │
│  │ • Cache      │         │ PostgreSQL   │                 │
│  └──────────────┘         │ (Cold Path)  │                 │
│                           │              │                 │
│                           │ • History    │                 │
│                           │ • Analytics  │                 │
│                           └──────────────┘                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Ambulance Service                         │
│  ┌──────────────┐         ┌──────────────┐                 │
│  │    Redis     │◄────────│    State     │                 │
│  │  (State)     │         │   Tracker    │                 │
│  └──────────────┘         └──────┬───────┘                 │
│                                   │                          │
│                                   ▼                          │
│                          ┌──────────────┐                   │
│                          │ PostgreSQL   │                   │
│                          │ (Audit Log)  │                   │
│                          └──────────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

---

## Data Flow Patterns

### Pattern 1: Emergency Creation (Write-Heavy)
```
1. Client → POST /api/emergencies
2. Emergency Service → PostgreSQL (INSERT)
3. Emergency Service → Kafka (PUBLISH)
4. Dispatch Service → Redis (QUEUE)
5. Dispatch Service → PostgreSQL (AUDIT)
```

### Pattern 2: Dispatch Algorithm (Read-Heavy)
```
1. Dispatch Engine → Redis (GET available ambulances)
2. Dispatch Engine → Redis GEO (GEORADIUS nearest)
3. Dispatch Engine → Redis (SETNX lock)
4. Dispatch Engine → Kafka (PUBLISH assignment)
5. Dispatch Engine → PostgreSQL (INSERT history)
```

### Pattern 3: State Transition (Mixed)
```
1. Ambulance Service → Redis (Lua script atomic update)
2. Ambulance Service → PostgreSQL (INSERT transition log)
3. Ambulance Service → Kafka (PUBLISH state change)
```

### Pattern 4: Analytics Query (Read-Only)
```
1. Analytics Service → PostgreSQL (Complex query)
2. Analytics Service → Redis (CACHE result)
3. Return to client
```

---

## Code Patterns

### Pattern 1: Dual Write (Emergency Creation)
```java
@Transactional
public Emergency createEmergency(EmergencyRequest request) {
    // 1. PostgreSQL (source of truth)
    Emergency emergency = emergencyRepository.save(buildEmergency(request));
    
    // 2. Kafka (async processing)
    kafkaTemplate.send("emergency-topic", mapToEvent(emergency));
    
    // 3. Redis (optional cache)
    cacheEmergency(emergency);
    
    return emergency;
}
```

### Pattern 2: Cache-Aside (Read)
```java
public Emergency getEmergency(String emergencyId) {
    // 1. Try Redis cache
    String cached = redisTemplate.opsForValue().get("emergency:" + emergencyId);
    if (cached != null) {
        return deserialize(cached);
    }
    
    // 2. Query PostgreSQL
    Emergency emergency = emergencyRepository.findByEmergencyId(emergencyId)
        .orElseThrow(() -> new EntityNotFoundException());
    
    // 3. Update cache
    redisTemplate.opsForValue().set(
        "emergency:" + emergencyId,
        serialize(emergency),
        Duration.ofHours(1)
    );
    
    return emergency;
}
```

### Pattern 3: Write-Through (State Update)
```java
@Transactional
public void updateAmbulanceState(String ambulanceId, AmbulanceStatus newStatus) {
    // 1. Update Redis (hot path)
    redisTemplate.opsForValue().set(
        "ambulance:" + ambulanceId + ":status",
        newStatus.name()
    );
    
    // 2. Update PostgreSQL (cold path)
    Ambulance ambulance = ambulanceRepository.findByAmbulanceId(ambulanceId)
        .orElseThrow();
    ambulance.setStatus(newStatus);
    ambulanceRepository.save(ambulance);
    
    // 3. Record transition
    stateTransitionRepository.save(buildTransition(ambulanceId, newStatus));
}
```

### Pattern 4: Event Sourcing (Audit Trail)
```java
@KafkaListener(topics = "ambulance-assigned-topic")
public void onAssignment(AssignmentEvent event) {
    // 1. Update Redis state (real-time)
    updateRedisState(event);
    
    // 2. Append to PostgreSQL log (audit)
    AssignmentHistory history = new AssignmentHistory();
    history.setEmergencyId(event.getEmergencyId());
    history.setAmbulanceId(event.getAmbulanceId());
    history.setAssignedAt(LocalDateTime.now());
    assignmentHistoryRepository.save(history);
}
```

---

## Performance Characteristics

### Redis Operations
| Operation | Complexity | Latency | Use Case |
|-----------|-----------|---------|----------|
| GET | O(1) | < 1ms | State lookup |
| SET | O(1) | < 1ms | State update |
| GEORADIUS | O(log n) | < 5ms | Nearest search |
| SETNX | O(1) | < 1ms | Distributed lock |
| Lua Script | O(1) | < 2ms | Atomic operations |

### PostgreSQL Operations
| Operation | Complexity | Latency | Use Case |
|-----------|-----------|---------|----------|
| INSERT | O(log n) | 5-10ms | Write history |
| SELECT by PK | O(log n) | 2-5ms | Lookup by ID |
| SELECT with GEO | O(log n) | 10-50ms | Spatial query |
| Complex JOIN | O(n log n) | 50-500ms | Analytics |
| Aggregation | O(n) | 100-1000ms | Reporting |

---

## Consistency Model

### Strong Consistency (Redis)
- Ambulance state transitions
- Distributed locks
- Idempotency checks

### Eventual Consistency (PostgreSQL)
- Assignment history
- State transition logs
- Location history

### Reconciliation Strategy
```java
@Scheduled(fixedRate = 300000) // Every 5 minutes
public void reconcileState() {
    // 1. Get all ambulances from Redis
    Set<String> redisAmbulances = getRedisAmbulances();
    
    // 2. Get all ambulances from PostgreSQL
    List<Ambulance> dbAmbulances = ambulanceRepository.findAll();
    
    // 3. Sync differences
    for (Ambulance db : dbAmbulances) {
        String redisStatus = getRedisStatus(db.getAmbulanceId());
        if (!db.getStatus().name().equals(redisStatus)) {
            // Redis is source of truth for current state
            db.setStatus(AmbulanceStatus.valueOf(redisStatus));
            ambulanceRepository.save(db);
        }
    }
}
```

---

## Monitoring

### Redis Metrics
```
redis.connections.active
redis.commands.processed
redis.memory.used
redis.geo.operations.latency
redis.lock.acquisitions
redis.lock.failures
```

### PostgreSQL Metrics
```
postgres.connections.active
postgres.transactions.committed
postgres.queries.duration.p99
postgres.table.size
postgres.index.usage
postgres.replication.lag
```

### Application Metrics
```
dispatch.redis.hits
dispatch.redis.misses
dispatch.postgres.writes
dispatch.postgres.reads
dispatch.cache.hit.ratio
```

---

## Failure Scenarios

### Scenario 1: Redis Down
```
Impact: Dispatch algorithm fails
Fallback: Query PostgreSQL for ambulance locations
Recovery: Rebuild Redis cache from PostgreSQL
```

### Scenario 2: PostgreSQL Down
```
Impact: No historical writes
Fallback: Continue with Redis (in-memory only)
Recovery: Replay Kafka events to rebuild history
```

### Scenario 3: Both Down
```
Impact: System unavailable
Fallback: Return 503 Service Unavailable
Recovery: Restore from backups
```

---

## Backup Strategy

### Redis Backup
```bash
# RDB snapshot every hour
save 3600 1

# AOF for durability
appendonly yes
appendfsync everysec
```

### PostgreSQL Backup
```bash
# Daily full backup
pg_dump emergency_dispatch > backup_$(date +%Y%m%d).sql

# Continuous WAL archiving
archive_mode = on
archive_command = 'cp %p /backup/wal/%f'
```

---

## Cost Analysis

### Redis (AWS ElastiCache)
- **Instance**: cache.r6g.large (13.5 GB)
- **Cost**: ~$150/month
- **Use**: Real-time state, locks, cache

### PostgreSQL (AWS RDS)
- **Instance**: db.r6g.xlarge (32 GB)
- **Storage**: 500 GB SSD
- **Cost**: ~$400/month
- **Use**: Historical data, analytics

### Total: ~$550/month for production-grade setup

---

## Interview Answers

### Q: Why not just use PostgreSQL for everything?
**A**: "PostgreSQL is great, but our dispatch algorithm needs sub-5ms response times. Redis GEO gives us O(log n) geospatial queries with < 5ms latency, while PostgreSQL would be 10-50ms. For real-time dispatch, that 10x speed difference matters. We use PostgreSQL for what it's best at: complex queries, analytics, and durable storage."

### Q: Why not just use Redis for everything?
**A**: "Redis is in-memory, so we'd lose all data on restart. For compliance and analytics, we need durable storage. PostgreSQL gives us ACID transactions, complex joins, and PostGIS for advanced geospatial analysis. The hybrid approach gives us both speed and durability."

### Q: How do you handle consistency between Redis and PostgreSQL?
**A**: "We use eventual consistency with PostgreSQL as the source of truth for historical data, and Redis as the source of truth for current state. We have a reconciliation job that syncs every 5 minutes. For critical operations, we use distributed transactions with Saga pattern."

### Q: What if Redis and PostgreSQL have different data?
**A**: "Redis is authoritative for current state (ambulance status, location). PostgreSQL is authoritative for history. If they diverge, we trust Redis for 'what is happening now' and PostgreSQL for 'what happened before'. Our reconciliation job ensures they stay in sync."

---

## Summary

**Hybrid Architecture Benefits:**
✅ Speed: Redis for < 5ms dispatch  
✅ Durability: PostgreSQL for permanent storage  
✅ Analytics: Complex queries with PostGIS  
✅ Scalability: Each system optimized for its use case  
✅ Reliability: Fallback strategies for failures  

**This architecture is production-ready and interview-ready!**
