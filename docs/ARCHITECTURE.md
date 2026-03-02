# Architecture Deep Dive

## Table of Contents
- [System Overview](#system-overview)
- [Microservices Design](#microservices-design)
- [Event-Driven Architecture](#event-driven-architecture)
- [State Management](#state-management)
- [Distributed Systems Patterns](#distributed-systems-patterns)
- [Scalability Considerations](#scalability-considerations)

## System Overview

The Emergency Dispatch System is built on microservices architecture principles with event-driven communication. Each service is independently deployable, scalable, and maintainable.

### Design Principles

1. **Single Responsibility**: Each service handles one domain
2. **Loose Coupling**: Services communicate via events
3. **High Cohesion**: Related functionality grouped together
4. **Fault Isolation**: Service failures don't cascade
5. **Independent Deployment**: Services can be deployed separately

## Microservices Design

### Emergency Service

**Responsibility**: Emergency request intake and validation

**Key Components**:
- `EmergencyController`: REST endpoint for emergency creation
- `EmergencyProducer`: Kafka producer for event publishing
- `EmergencyEvent`: DTO with validation constraints

**Design Decisions**:
- Synchronous HTTP for client interaction (user expects immediate response)
- Asynchronous Kafka for internal processing (decoupling)
- Input validation at entry point (fail fast)

### Dispatch Service

**Responsibility**: Intelligent ambulance dispatch coordination

**Key Components**:
- `DispatchListener`: Kafka consumer for emergency events
- `DispatchEngine`: Core dispatch algorithm with scheduled execution
- Priority-based Redis queues (HIGH, MEDIUM, LOW)

**Algorithm**:
```
1. Poll Redis queues by priority (HIGH → MEDIUM → LOW)
2. For each emergency:
   a. Find all available ambulances from local cache
   b. Calculate distance using Haversine formula
   c. Select nearest ambulance
   d. Acquire distributed lock
   e. Publish assignment event
   f. Remove from queue
```

**Design Decisions**:
- Scheduled polling (1-second interval) for controlled dispatch rate
- In-memory ambulance cache for performance
- Redis for durable queue and distributed locking
- Haversine formula for accurate distance calculation

### Ambulance Service

**Responsibility**: Fleet state management and lifecycle tracking

**Key Components**:
- `AmbulanceStateTracker`: FSM implementation with Redis
- `AmbulanceAssignmentListener`: Assignment event consumer
- `AmbulanceLocationScheduler`: Periodic location updates
- Lua scripts for atomic Redis operations

**Finite State Machine**:
```
States: AVAILABLE → ASSIGNED → ON_ROUTE → ARRIVED → COMPLETED → AVAILABLE
```

**Design Decisions**:
- Optimistic locking with version numbers
- Atomic state transitions using Lua scripts
- Auto-healing for stuck states
- Periodic location broadcasts

## Event-Driven Architecture

### Kafka Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `emergency-topic` | Emergency Service | Dispatch Service | Emergency requests |
| `ambulance-assigned-topic` | Dispatch Service | Ambulance Service | Assignment events |
| `ambulance-location-topic` | Ambulance Service | Dispatch Service | Location updates |
| `ambulance-completed-topic` | Ambulance Service | Dispatch Service | Completion events |
| `*.dlt` | Error Handler | Manual Review | Failed messages |

### Event Schema Evolution

All events use JSON serialization with schema-less approach. For production:
- Consider Avro/Protobuf for schema evolution
- Implement schema registry (Confluent Schema Registry)
- Version events explicitly

### Dead Letter Topics (DLT)

Failed messages are routed to DLT for:
- Manual inspection
- Replay after fixes
- Alerting and monitoring

## State Management

### Redis Data Structures

**Ambulance State**:
```
ambulance:{id}:status → "AVAILABLE" | "ASSIGNED" | "ON_ROUTE" | "ARRIVED" | "COMPLETED"
ambulance:{id}:version → Long (optimistic lock version)
ambulance:{id}:lastUpdated → Timestamp (for auto-healing)
ambulance:{id}:activeEmergencyId → Emergency ID (for tracking)
```

**Emergency Queues**:
```
dispatch:queue:HIGH → List<EmergencyEvent>
dispatch:queue:MEDIUM → List<EmergencyEvent>
dispatch:queue:LOW → List<EmergencyEvent>
```

**Distributed Locks**:
```
lock:ambulance:{id} → "locked" (TTL: 5 seconds)
```

### Consistency Model

- **Eventual Consistency**: System state converges over time
- **Optimistic Locking**: Version-based conflict detection
- **Atomic Operations**: Lua scripts for multi-key operations

## Distributed Systems Patterns

### 1. Event Sourcing (Partial)
- Events capture state changes
- Event log provides audit trail
- Can reconstruct state from events

### 2. CQRS (Command Query Responsibility Segregation)
- Commands: Emergency creation, state transitions
- Queries: Ambulance availability, queue depth
- Separate read/write models

### 3. Saga Pattern
- Distributed transaction across services
- Compensating actions for failures
- Example: Assignment → Acceptance → Completion

### 4. Circuit Breaker
- Kafka retry with exponential backoff
- DLT for persistent failures
- Service health checks

### 5. Distributed Locking
- Redis-based locks with TTL
- Prevents double-assignment
- Automatic lock release on timeout

### 6. Auto-Healing
- Detects stuck states
- Automatic recovery without manual intervention
- Metrics for monitoring healing events

## Scalability Considerations

### Horizontal Scaling

**Stateless Services** (Easy to scale):
- Emergency Service
- Notification Service
- API Gateway

**Stateful Services** (Requires coordination):
- Dispatch Service: Single instance recommended (or use leader election)
- Ambulance Service: Can scale with partition-based processing

### Kafka Partitioning

Current: Single partition per topic

For scale:
```
emergency-topic: Partition by region/zone
ambulance-assigned-topic: Partition by ambulance ID
```

### Redis Scaling

Current: Single Redis instance

For scale:
- Redis Cluster for horizontal scaling
- Redis Sentinel for high availability
- Separate Redis instances per service

### Performance Optimizations

1. **Caching**: Ambulance locations cached in Dispatch Service
2. **Batching**: Could batch location updates
3. **Indexing**: Redis sorted sets for geo-queries
4. **Connection Pooling**: Kafka and Redis connection pools

### Bottlenecks

1. **Dispatch Service**: Single-threaded dispatch loop
   - Solution: Parallel processing with partitioning
   
2. **Redis**: Single point of contention
   - Solution: Sharding by ambulance ID
   
3. **Kafka**: Single partition limits throughput
   - Solution: Multiple partitions with key-based routing

## Monitoring Strategy

### Metrics Hierarchy

**Business Metrics**:
- Emergency response time
- Assignment success rate
- Ambulance utilization

**Technical Metrics**:
- Request latency (p50, p95, p99)
- Error rates
- Queue depth

**Infrastructure Metrics**:
- CPU, memory, disk usage
- Network throughput
- Container health

### Alerting Rules

- Queue depth > 100 for 5 minutes
- Assignment failure rate > 5%
- No available ambulances for 2 minutes
- Service health check failures

## Security Considerations

### Current State
- No authentication/authorization
- Internal network only

### Production Requirements
- API Gateway authentication (JWT/OAuth2)
- Service-to-service mTLS
- Kafka ACLs
- Redis AUTH
- Network policies
- Secrets management (Vault/AWS Secrets Manager)

## Future Enhancements

1. **Database Persistence**: PostgreSQL for historical data
2. **Geospatial Indexing**: PostGIS or Redis Geo commands
3. **Real-time Updates**: WebSocket for live tracking
4. **Machine Learning**: Predictive dispatch, demand forecasting
5. **Multi-Region**: Geographic distribution
6. **Service Mesh**: Istio for advanced traffic management
