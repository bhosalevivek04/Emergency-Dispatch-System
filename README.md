# Emergency Dispatch System

A production-grade microservices-based emergency dispatch system built with Spring Boot, Kafka, Redis, and PostgreSQL.

## Architecture

### Services
- **emergency-service** (8081) - Emergency management and creation
- **ambulance-service** (8082) - Ambulance fleet management and FSM
- **dispatch-service** (8083) - Automatic dispatch engine with priority queueing
- **tracking-service** (8085) - Real-time location tracking with WebSocket
- **auth-service** (8086) - JWT authentication with RS256
- **api-gateway** (8080) - API Gateway with rate limiting and JWT validation

### Infrastructure
- **PostgreSQL** - Persistent storage
- **Redis** - Distributed state management and caching
- **Kafka** - Event streaming
- **OSRM** - Route optimization

## Features

- ✅ Event-driven microservices architecture
- ✅ Automatic dispatch with priority-based queueing (HIGH → MEDIUM → LOW)
- ✅ Finite State Machine (FSM) for ambulance lifecycle
- ✅ Real-time location tracking via WebSocket
- ✅ JWT authentication with RS256 (asymmetric keys)
- ✅ Service-level authorization (RBAC)
- ✅ Distributed rate limiting (Redis-based)
- ✅ Transactional outbox pattern
- ✅ Idempotency handling
- ✅ Atomic state transitions (Lua scripts)
- ✅ Auto-heal mechanism for stuck ambulances
- ✅ Prometheus metrics
- ✅ Health checks

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 15+
- Redis 7+
- Kafka 3.x

## Quick Start

### 1. Start Infrastructure

```bash
docker-compose up -d postgres redis kafka osrm-pune
```

### 2. Start Services

Start services in Spring Tool Suite (STS) or via Maven:

```bash
# Start in order
cd emergency-service && mvn spring-boot:run
cd ambulance-service && mvn spring-boot:run
cd dispatch-service && mvn spring-boot:run
cd tracking-service && mvn spring-boot:run
cd auth-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
```

### 3. Verify Services

```bash
# Check health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## API Documentation

### Authentication

```bash
# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"dispatcher1","password":"password123"}'

# Response: { "token": "eyJhbGc..." }
```

### Create Emergency

```bash
curl -X POST http://localhost:8080/emergency \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "emergencyId": "EMG-001",
    "lat": 18.5204,
    "lon": 73.8567,
    "priority": "HIGH"
  }'
```

### Track Ambulance Location

```bash
curl -X POST http://localhost:8080/tracking/location \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "ambulanceId": "AMB-101",
    "latitude": 18.5204,
    "longitude": 73.8567,
    "timestamp": 1234567890
  }'
```

## Configuration

### Environment Variables

Create `.env` file (see `.env.example`):

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=emergency_dispatch
DB_USER=dispatch_user
DB_PASSWORD=dispatch_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### Service Ports

| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Emergency Service | 8081 |
| Ambulance Service | 8082 |
| Dispatch Service | 8083 |
| Tracking Service | 8085 |
| Auth Service | 8086 |

## Monitoring

### Metrics

```bash
# Dispatch metrics
curl http://localhost:8083/actuator/metrics/dispatch.ambulances.available.count
curl http://localhost:8083/actuator/metrics/dispatch.assignments.published.total

# Emergency metrics
curl http://localhost:8081/actuator/metrics/emergency.requests.total

# Ambulance metrics
curl http://localhost:8082/actuator/metrics/ambulance.fsm.transitions.total
```

### Health Checks

```bash
curl http://localhost:8080/actuator/health
```

## Development

### Testing & Debugging Tools

See `dev-tools/` directory for:
- Demo scripts
- Testing utilities
- Debugging tools
- Development documentation

```bash
# Run complete demo
./dev-tools/FINAL-WORKING-DEMO.ps1

# See all available tools
ls dev-tools/
```

### Database Schema

```sql
-- Emergencies table
CREATE TABLE emergencies (
    emergency_id VARCHAR(50) PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_ambulance_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Assignment history
CREATE TABLE assignment_history (
    id BIGSERIAL PRIMARY KEY,
    emergency_id VARCHAR(50) NOT NULL,
    ambulance_id VARCHAR(50) NOT NULL,
    distance_km DOUBLE PRECISION,
    assignment_version INTEGER,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Architecture Patterns

### Event-Driven Design
- Kafka topics: `emergency-topic`, `ambulance-location-topic`, `ambulance-assigned-topic`
- Transactional outbox pattern for reliable event publishing
- Idempotency keys for duplicate message handling

### State Management
- Redis for distributed state (ambulance status, versions, locks)
- Atomic state transitions using Lua scripts
- Optimistic locking with version numbers

### Dispatch Algorithm
1. Emergency created → Queued by priority (HIGH/MEDIUM/LOW)
2. Dispatch engine processes queue every 1 second
3. Find nearest available ambulance using OSRM routing
4. Acquire distributed lock (Redis)
5. Publish assignment event to Kafka
6. Ambulance FSM transitions: AVAILABLE → ASSIGNED → ON_ROUTE → ARRIVED → COMPLETED

## Security

### Authentication
- JWT with RS256 (asymmetric encryption)
- 2048-bit RSA keys
- Token expiration: 24 hours

### Authorization
- Role-based access control (RBAC)
- Roles: DISPATCHER, AMBULANCE_DRIVER, ADMIN
- Service-level authorization with @PreAuthorize

### Rate Limiting
- Redis-based distributed rate limiting
- Per-endpoint limits:
  - Auth: 5 requests/minute
  - Emergency: 10 requests/minute
  - Location: 60 requests/minute
  - Read: 100 requests/minute

## Production Deployment

### Docker Compose

```bash
docker-compose up -d
```

### Kubernetes

See `k8s/` directory for Kubernetes manifests (if available).

## Troubleshooting

### No Ambulances Available

If all ambulances are stuck in ASSIGNED/ON_ROUTE:

```bash
# Option 1: Wait for auto-heal (runs every 60 seconds)
# Option 2: Use dev tools
./dev-tools/force-reset-ambulances.ps1
```

### Kafka Connection Issues

```bash
# Check Kafka is running
docker ps | grep kafka

# Check topics
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Redis Connection Issues

```bash
# Check Redis is running
docker ps | grep redis

# Test connection
redis-cli ping
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

[Your License Here]

## Contact

[Your Contact Information]

---

**Built with ❤️ using Spring Boot, Kafka, Redis, and PostgreSQL**
