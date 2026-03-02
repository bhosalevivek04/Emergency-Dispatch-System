# 🏗️ Microservices Architecture - Emergency Dispatch System

## 🎯 Single Responsibility Principle Applied

Each service has ONE clear responsibility, making the system scalable, maintainable, and independently deployable.

---

## 📊 Service Overview

| Service | Port | Responsibility | Database/Cache |
|---------|------|----------------|----------------|
| **API Gateway** | 8080 | Route requests, CORS, Load balancing | None |
| **Emergency Service** | 8081 | Handle emergency requests | None (Event producer) |
| **Ambulance Service** | 8082 | Manage ambulance fleet & status | Redis |
| **Dispatch Service** | 8083 | Intelligent ambulance assignment | Redis |
| **Notification Service** | 8084 | Send notifications (SMS/Email/Push) | None |
| **Tracking Service** | 8085 | Real-time location tracking | Redis + WebSocket |

---

## 🔄 Complete System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         REACT FRONTEND                          │
│                      http://localhost:3000                      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ All requests go through Gateway
                             ↓
┌─────────────────────────────────────────────────────────────────┐
│                       API GATEWAY (8080)                        │
│                                                                 │
│  Responsibilities:                                              │
│    • Route requests to appropriate services                     │
│    • CORS handling                                              │
│    • Load balancing                                             │
│    • Rate limiting (future)                                     │
│    • Authentication/Authorization (future)                      │
│                                                                 │
│  Routes:                                                        │
│    /api/emergencies/**    → Emergency Service (8081)            │
│    /api/ambulances/**     → Ambulance Service (8082)            │
│    /api/dispatch/**       → Dispatch Service (8083)             │
│    /api/notifications/**  → Notification Service (8084)         │
│    /api/tracking/**       → Tracking Service (8085)             │
│    /ws/**                 → Tracking Service WebSocket          │
└─────────────────────────────────────────────────────────────────┘
         │              │              │              │
         ↓              ↓              ↓              ↓
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│  EMERGENCY   │ │  AMBULANCE   │ │   DISPATCH   │ │ NOTIFICATION │
│   SERVICE    │ │   SERVICE    │ │   SERVICE    │ │   SERVICE    │
│   (8081)     │ │   (8082)     │ │   (8083)     │ │   (8084)     │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
         │              │              │              │
         └──────────────┴──────────────┴──────────────┘
                        │
                        ↓
         ┌──────────────────────────────┐
         │      KAFKA EVENT BUS         │
         │                              │
         │  Topics:                     │
         │    • emergency-topic         │
         │    • ambulance-location      │
         │    • ambulance-assigned      │
         │    • ambulance-completed     │
         │    • notification-topic      │
         └──────────────────────────────┘
                        │
         ┌──────────────┴──────────────┐
         ↓                             ↓
┌──────────────┐              ┌──────────────┐
│    REDIS     │              │   TRACKING   │
│   (6379)     │              │   SERVICE    │
│              │              │   (8085)     │
│  Stores:     │              │              │
│  • Ambulance │              │  WebSocket   │
│    locations │              │  Real-time   │
│  • Status    │              │  Updates     │
│  • Queues    │              └──────────────┘
└──────────────┘                      │
                                      ↓
                              ┌──────────────┐
                              │  MONITORING  │
                              │              │
                              │  Prometheus  │
                              │  Grafana     │
                              └──────────────┘
```

---

## 🎯 Service Responsibilities (Single Responsibility)

### 1️⃣ API Gateway (Port 8080)
**Single Responsibility**: Entry point and request routing

**What it does**:
- Routes all external requests to appropriate microservices
- Handles CORS for frontend
- Provides single entry point for clients
- Can add authentication/authorization layer

**What it does NOT do**:
- ❌ Business logic
- ❌ Data storage
- ❌ Event processing

**Endpoints**:
```
GET  /api/emergencies/**     → Emergency Service
POST /api/emergencies/**     → Emergency Service
GET  /api/ambulances/**      → Ambulance Service
POST /api/ambulances/**      → Ambulance Service
GET  /api/dispatch/**        → Dispatch Service
POST /api/dispatch/**        → Dispatch Service
GET  /api/tracking/**        → Tracking Service
WS   /ws/**                  → Tracking Service
```

---

### 2️⃣ Emergency Service (Port 8081)
**Single Responsibility**: Handle emergency requests from citizens

**What it does**:
- Accept emergency requests from frontend/mobile
- Validate emergency data (location, priority, type)
- Generate unique emergency ID
- Publish emergency event to Kafka
- Provide emergency status queries

**What it does NOT do**:
- ❌ Assign ambulances (that's Dispatch Service)
- ❌ Track ambulances (that's Tracking Service)
- ❌ Send notifications (that's Notification Service)

**Endpoints**:
```
POST /emergency              - Create new emergency
GET  /emergency/{id}         - Get emergency status
GET  /emergency/active       - List active emergencies
PUT  /emergency/{id}/cancel  - Cancel emergency
```

**Kafka Events Produced**:
- `emergency-topic` - New emergency created

**Database**: None (stateless, events stored in Kafka)

---

### 3️⃣ Ambulance Service (Port 8082)
**Single Responsibility**: Manage ambulance fleet and status

**What it does**:
- Register new ambulances
- Manage ambulance status (AVAILABLE, ASSIGNED, ON_ROUTE, ARRIVED, COMPLETED)
- Store ambulance metadata (vehicle info, driver, equipment)
- Handle ambulance availability
- Simulate ambulance state transitions

**What it does NOT do**:
- ❌ Track real-time locations (that's Tracking Service)
- ❌ Assign ambulances to emergencies (that's Dispatch Service)
- ❌ Calculate routes (that's Dispatch Service with OSRM)

**Endpoints**:
```
POST /ambulance              - Register ambulance
GET  /ambulance/{id}         - Get ambulance details
GET  /ambulance/available    - List available ambulances
PUT  /ambulance/{id}/status  - Update status
```

**Kafka Events**:
- Consumes: `ambulance-assigned-topic` - Updates status to ASSIGNED
- Produces: `ambulance-location-topic` - Simulated location updates
- Produces: `ambulance-completed-topic` - Trip completion

**Database**: Redis
- `ambulance:{id}:status` - Current status
- `ambulance:{id}:version` - Version for optimistic locking
- `ambulance:{id}:metadata` - Vehicle details

---

### 4️⃣ Dispatch Service (Port 8083)
**Single Responsibility**: Intelligent ambulance assignment

**What it does**:
- Listen to emergency events
- Get available ambulances from Redis
- Calculate optimal ambulance using OSRM
- Select ambulance with minimum ETA
- Publish assignment event
- Manage priority queues (HIGH/MEDIUM/LOW)
- Handle distributed locking

**What it does NOT do**:
- ❌ Create emergencies (that's Emergency Service)
- ❌ Manage ambulance fleet (that's Ambulance Service)
- ❌ Send notifications (that's Notification Service)
- ❌ Track locations (that's Tracking Service)

**Endpoints**:
```
POST /dispatch/emergency     - Manual dispatch trigger
GET  /dispatch/queue         - View dispatch queue
GET  /dispatch/metrics       - Dispatch metrics
```

**Kafka Events**:
- Consumes: `emergency-topic` - New emergencies
- Consumes: `ambulance-location-topic` - Real-time locations
- Consumes: `ambulance-completed-topic` - Availability updates
- Produces: `ambulance-assigned-topic` - Assignment decisions

**Database**: Redis
- `dispatch:queue:HIGH` - High priority queue
- `dispatch:queue:MEDIUM` - Medium priority queue
- `dispatch:queue:LOW` - Low priority queue
- `lock:ambulance:{id}` - Distributed locks

**External APIs**:
- OSRM - Route calculation

---

### 5️⃣ Notification Service (Port 8084)
**Single Responsibility**: Send notifications to users

**What it does**:
- Listen to assignment events
- Send SMS to patient (Twilio/AWS SNS)
- Send push notifications to ambulance driver
- Send email confirmations
- Log notification delivery status

**What it does NOT do**:
- ❌ Assign ambulances (that's Dispatch Service)
- ❌ Track ambulances (that's Tracking Service)
- ❌ Manage emergencies (that's Emergency Service)

**Endpoints**:
```
GET /notification/status/{id}  - Check notification status
POST /notification/resend      - Resend notification
```

**Kafka Events**:
- Consumes: `ambulance-assigned-topic` - Send assignment notifications
- Consumes: `ambulance-completed-topic` - Send completion notifications
- Produces: `notification-topic` - Notification delivery status

**Database**: None (can add for notification history)

**External APIs**:
- Twilio (SMS)
- Firebase (Push notifications)
- SendGrid (Email)

---

### 6️⃣ Tracking Service (Port 8085)
**Single Responsibility**: Real-time location tracking and WebSocket updates

**What it does**:
- Receive ambulance location updates
- Store locations in Redis
- Broadcast updates via WebSocket
- Provide real-time map data to frontend
- Calculate distance traveled
- Track route history

**What it does NOT do**:
- ❌ Assign ambulances (that's Dispatch Service)
- ❌ Manage ambulance status (that's Ambulance Service)
- ❌ Send notifications (that's Notification Service)

**Endpoints**:
```
POST /tracking/location      - Update ambulance location
GET  /tracking/ambulances    - Get all ambulance locations
GET  /tracking/{id}/history  - Get location history
WS   /ws/tracking            - WebSocket for real-time updates
```

**Kafka Events**:
- Consumes: `ambulance-location-topic` - Location updates
- Produces: WebSocket messages to frontend

**Database**: Redis
- `location:{ambulanceId}` - Current location
- `location:{ambulanceId}:history` - Location history (list)

---

## 🔥 Event Flow Examples

### Example 1: Emergency Request Flow

```
1. User clicks map in React
   ↓
2. POST /api/emergencies → API Gateway → Emergency Service
   ↓
3. Emergency Service validates and publishes to Kafka
   Topic: emergency-topic
   ↓
4. Dispatch Service consumes event
   ↓
5. Dispatch Service:
   - Gets available ambulances from Redis
   - Calls OSRM for each ambulance
   - Selects minimum ETA
   - Publishes to ambulance-assigned-topic
   ↓
6. Three services consume assignment:
   a) Ambulance Service → Updates status to ASSIGNED
   b) Notification Service → Sends SMS/Push
   c) Tracking Service → Broadcasts via WebSocket
   ↓
7. React UI receives WebSocket update
   - Shows assigned ambulance
   - Draws route on map
   - Displays ETA
```

### Example 2: Ambulance Location Update Flow

```
1. Ambulance GPS device sends location
   ↓
2. POST /api/ambulances/location → API Gateway → Ambulance Service
   ↓
3. Ambulance Service publishes to Kafka
   Topic: ambulance-location-topic
   ↓
4. Two services consume:
   a) Dispatch Service → Updates local cache
   b) Tracking Service → Stores in Redis + WebSocket broadcast
   ↓
5. React UI receives WebSocket update
   - Updates ambulance marker position
   - Updates route progress
```

---

## 📈 Scalability Strategy

### Horizontal Scaling

Each service can scale independently:

```
API Gateway:       3 instances (load balanced)
Emergency Service: 2 instances (stateless)
Ambulance Service: 2 instances (Redis shared state)
Dispatch Service:  3 instances (high load, Redis locks prevent conflicts)
Notification:      5 instances (high throughput)
Tracking Service:  2 instances (WebSocket sticky sessions)
```

### Why This Scales

1. **Stateless Services**: Emergency, Notification services have no state
2. **Shared State**: Redis provides shared state for Ambulance, Dispatch
3. **Event-Driven**: Kafka handles async communication
4. **Independent Deployment**: Update one service without affecting others
5. **Technology Freedom**: Each service can use different tech stack

---

## 🔧 Configuration Summary

### Ports
- 8080: API Gateway
- 8081: Emergency Service
- 8082: Ambulance Service
- 8083: Dispatch Service
- 8084: Notification Service
- 8085: Tracking Service
- 9092: Kafka
- 6379: Redis
- 9090: Prometheus
- 3000: Grafana
- 3000: React Frontend

### Kafka Topics
- `emergency-topic` - Emergency requests
- `ambulance-location-topic` - Location updates
- `ambulance-assigned-topic` - Assignment events
- `ambulance-completed-topic` - Trip completions
- `notification-topic` - Notification status

### Redis Keys
- `ambulance:{id}:status`
- `ambulance:{id}:version`
- `location:{id}`
- `dispatch:queue:{priority}`
- `lock:ambulance:{id}`

---

## 🚀 Deployment Order

1. Start Infrastructure:
   ```bash
   redis-server
   kafka-server-start
   ```

2. Start Services (order matters):
   ```bash
   # Core services first
   cd ambulance-service && ./mvnw spring-boot:run
   cd emergency-service && ./mvnw spring-boot:run
   cd dispatch-service && ./mvnw spring-boot:run
   
   # Supporting services
   cd notification-service && ./mvnw spring-boot:run
   cd tracking-service && ./mvnw spring-boot:run
   
   # Gateway last
   cd api-gateway && ./mvnw spring-boot:run
   ```

3. Start Frontend:
   ```bash
   cd ambulance-tracking-frontend && npm start
   ```

---

## 🎓 Benefits of This Architecture

✅ **Single Responsibility**: Each service does ONE thing well
✅ **Independent Scaling**: Scale services based on load
✅ **Independent Deployment**: Deploy without downtime
✅ **Technology Freedom**: Use best tool for each job
✅ **Fault Isolation**: One service failure doesn't crash system
✅ **Team Autonomy**: Different teams can own different services
✅ **Easy Testing**: Test services in isolation
✅ **Clear Boundaries**: Well-defined service contracts

---

## 🔥 This is Production-Grade Microservices!

Perfect for:
- System design interviews
- Portfolio projects
- Real-world applications
- Learning distributed systems
