# Emergency Dispatch System

Production-grade, microservices-based emergency response platform for rapid ambulance dispatch, real-time tracking, and citizen-facing emergency request UX.

## Pitch Summary

Emergency Dispatch System reduces ambulance response latency by combining:
- priority-aware dispatch automation,
- real-time fleet telemetry,
- resilient event-driven architecture,
- and a citizen request + live tracking experience.

It is designed for demo-to-production progression with observability, fail-safes, and operational guardrails.

## Core Value

- Faster assignment: nearest available ambulance selection with routing.
- Better reliability: outbox, idempotency, retry/circuit-breaker patterns.
- Better transparency: dispatcher + citizen views with live status/timeline.
- Production-ready ops: health probes, startup dependency checks, metrics, correlation IDs.

## Architecture

### Microservices
- `api-gateway` (8080): routing, JWT validation, rate limiting, correlation propagation.
- `auth-service` (8086): login/refresh/logout, JWT RS256, refresh token rotation.
- `emergency-service` (8081): emergency lifecycle + outbox publishing.
- `dispatch-service` (8083): priority queue, assignment engine, dispatch metrics.
- `ambulance-service` (8082): fleet state machine, movement simulation, auto-heal logic.
- `tracking-service` (8085): location ingestion + WebSocket broadcasting.
- `notification-service` (8084): Kafka consumer + webhook notifications.

### Infrastructure
- PostgreSQL: persistent emergency/assignment/auth data.
- Redis: distributed state, queueing, locks, rate limiting.
- Kafka: event backbone across services.
- OSRM: routing for distance/ETA logic.

## Key Features

- Event-driven microservices workflow
- Priority dispatch (`HIGH -> MEDIUM -> LOW`)
- Ambulance FSM (`AVAILABLE -> ASSIGNED -> ON_ROUTE -> ARRIVED -> COMPLETED`)
- Transactional outbox + idempotency protections
- Redis-backed atomic state transitions and distributed locking
- Auto-heal for stuck ambulance states
- WebSocket live updates for fleet and emergencies
- Citizen emergency request page with:
  - map-based location selection,
  - "Use My Location",
  - human-readable address resolution,
  - route path + ETA + distance to assigned ambulance
- Notification webhook integration for assignment events

## Recent Enhancements (Latest)

- Circuit breaker + retry integration for OSRM calls
- Correlation ID propagation + structured logging pattern
- Health readiness/liveness probe groups across services
- Startup dependency checks (DB/Redis/Kafka where applicable)
- Frontend UX upgrades:
  - post-submit citizen tracking state,
  - emergency lifecycle timeline,
  - live/offline connection indicators,
  - fallback polling when real-time channel is down,
  - accessibility and form-state improvements
- Route visualization in citizen map with estimated arrival time

## Security

- JWT authentication (RS256)
- Role-based dashboard access (`ADMIN`, `DISPATCHER`, `AMBULANCE_DRIVER`)
- Gateway enforcement of token validation for protected routes
- Refresh token rotation (short-lived access + renewable refresh)
- Distributed rate limiting at gateway

## Monitoring and Reliability

- Actuator endpoints enabled for health/info/metrics/prometheus
- Readiness/liveness probes configured
- Prometheus metrics instrumentation
- Structured logs include correlation IDs for traceability

## Local Setup

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker + Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d postgres redis kafka osrm-pune
```

### 2. Start Backend Services

Run in this order (STS or terminal):

```bash
cd emergency-service && mvn spring-boot:run
cd ambulance-service && mvn spring-boot:run
cd dispatch-service && mvn spring-boot:run
cd tracking-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd auth-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
```

### 3. Start Frontend

```bash
cd tracking-client
npm install
npm start
```

Frontend typically runs on `http://localhost:3002` (or CRA default if configured differently).

## Demo Flow (Pitch Ready)

1. Open citizen app: `/citizen`
2. Mark emergency location on map or use current location
3. Submit request with priority/details
4. Show dispatcher dashboard live receiving emergency
5. Show auto-assignment and ambulance movement
6. Show citizen route path, ETA, and status timeline updates
7. Show webhook payload received in `webhook.site`

## Important Endpoints

### Auth
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`

### Citizen Public Flow
- `POST /api/emergencies/public`
- `GET /api/emergencies/public/{emergencyId}`
- `GET /api/tracking/public/ambulances/{ambulanceId}`

### Internal Protected APIs (via Gateway)
- `/api/emergencies/**`
- `/api/ambulances/**`
- `/api/dispatch/**`
- `/api/tracking/**`
- `/api/notifications/**`

### Health
- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

## Environment Variables

Use `.env` / service `application.yml` overrides for:
- DB host/user/password
- Redis host/port
- Kafka bootstrap servers
- OSRM URL
- webhook URL and enable flag
- startup dependency check toggles

## Troubleshooting

### Service fails with readiness membership error

If health group includes a contributor that is not present, remove it from readiness include list or disable strict validation.

### No ambulance assigned

- Verify fleet initialization
- Check dispatch-service logs and Redis queue state
- Ensure Kafka and OSRM are reachable

### Citizen tracking not updating

- Check tracking-service WebSocket endpoint
- Verify fallback polling path via gateway
- Confirm assigned ambulance is publishing location updates

## Repository Notes

- Audit and improvement notes exist in project docs (see `docs/`, `CRITICAL-BUGS-FIXED.md`, and `CHANGES-SUMMARY.md`).
- Dev scripts available under `dev-tools/`.

---

Built for high-visibility emergency response demos and production hardening tracks.
