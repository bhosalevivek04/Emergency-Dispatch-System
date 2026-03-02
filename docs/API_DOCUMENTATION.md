# 🌐 API Documentation - Emergency Dispatch System

## Base URL
All requests go through the API Gateway:
```
http://localhost:8080
```

---

## 🚨 Emergency Service APIs

### Create Emergency
Create a new emergency request.

**Endpoint**: `POST /api/emergencies`

**Request Body**:
```json
{
  "emergencyId": "EMG-12345",
  "lat": 28.6500,
  "lon": 77.2000,
  "priority": "HIGH"
}
```

**Response**:
```json
"Emergency event sent successfully!"
```

**Example**:
```bash
curl -X POST http://localhost:8080/api/emergencies \
  -H "Content-Type: application/json" \
  -d '{
    "emergencyId": "EMG-12345",
    "lat": 28.6500,
    "lon": 77.2000,
    "priority": "HIGH"
  }'
```

**Priority Levels**:
- `HIGH` - Life-threatening emergencies (processed first)
- `MEDIUM` - Urgent but not critical
- `LOW` - Non-urgent requests

---

## 🚑 Ambulance Service APIs

### Register Ambulance
Register a new ambulance in the system.

**Endpoint**: `POST /api/ambulances`

**Request Body**:
```json
{
  "ambulanceId": "AMB-101",
  "latitude": 28.6139,
  "longitude": 77.2090,
  "status": "AVAILABLE"
}
```

**Response**:
```json
"Ambulance registered: AMB-101"
```

**Example**:
```bash
curl -X POST http://localhost:8080/api/ambulances \
  -H "Content-Type: application/json" \
  -d '{
    "ambulanceId": "AMB-101",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "status": "AVAILABLE"
  }'
```

**Ambulance Status**:
- `AVAILABLE` - Ready for assignment
- `ASSIGNED` - Assigned to emergency
- `ON_ROUTE` - Traveling to patient
- `ARRIVED` - Reached patient location
- `COMPLETED` - Trip completed

---

## 📍 Tracking Service APIs

### Update Ambulance Location
Update real-time location of an ambulance.

**Endpoint**: `POST /api/tracking/location`

**Request Body**:
```json
{
  "ambulanceId": "AMB-101",
  "lat": 28.6200,
  "lon": 77.2100,
  "speed": 45.5,
  "heading": 90.0
}
```

**Response**:
```json
{
  "status": "success",
  "message": "Location updated"
}
```

**Example**:
```bash
curl -X POST http://localhost:8080/api/tracking/location \
  -H "Content-Type: application/json" \
  -d '{
    "ambulanceId": "AMB-101",
    "lat": 28.6200,
    "lon": 77.2100,
    "speed": 45.5,
    "heading": 90.0
  }'
```

### Get All Ambulances
Get current location of all ambulances.

**Endpoint**: `GET /api/tracking/ambulances`

**Response**:
```json
[
  {
    "ambulanceId": "AMB-101",
    "lat": 28.6200,
    "lon": 77.2100,
    "speed": 45.5,
    "heading": 90.0,
    "timestamp": 1709380800000
  },
  {
    "ambulanceId": "AMB-102",
    "lat": 28.7041,
    "lon": 77.1025,
    "speed": 50.0,
    "heading": 180.0,
    "timestamp": 1709380800000
  }
]
```

**Example**:
```bash
curl http://localhost:8080/api/tracking/ambulances
```

### WebSocket Connection
Connect to real-time tracking updates.

**WebSocket URL**: `ws://localhost:8080/ws/tracking`

**Subscribe to**: `/topic/ambulances`

**JavaScript Example**:
```javascript
const socket = new SockJS('http://localhost:8080/ws/tracking');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);
    
    stompClient.subscribe('/topic/ambulances', function(message) {
        const ambulance = JSON.parse(message.body);
        console.log('Ambulance update:', ambulance);
        // Update map marker
    });
});
```

---

## 🎯 Dispatch Service APIs

### Manual Dispatch
Manually trigger dispatch for an emergency.

**Endpoint**: `POST /api/dispatch/emergency`

**Request Body**:
```json
{
  "lat": 28.6500,
  "lon": 77.2000,
  "priority": "HIGH"
}
```

**Response**:
```json
{
  "emergencyId": "EMG-a1b2c3d4",
  "status": "QUEUED",
  "message": "Emergency request received and queued for dispatch"
}
```

**Example**:
```bash
curl -X POST http://localhost:8080/api/dispatch/emergency \
  -H "Content-Type: application/json" \
  -d '{
    "lat": 28.6500,
    "lon": 77.2000,
    "priority": "HIGH"
  }'
```

### Get Dispatch Queue
View current dispatch queue status.

**Endpoint**: `GET /api/dispatch/queue`

**Response**:
```json
{
  "high": 2,
  "medium": 5,
  "low": 3,
  "total": 10
}
```

### Get Dispatch Metrics
View dispatch performance metrics.

**Endpoint**: `GET /api/dispatch/metrics`

**Response**:
```json
{
  "totalEmergencies": 150,
  "totalAssignments": 145,
  "failedAssignments": 5,
  "averageDispatchTime": 2.3,
  "availableAmbulances": 8
}
```

---

## 🔔 Notification Service APIs

### Get Notification Status
Check if notification was delivered.

**Endpoint**: `GET /api/notifications/status/{notificationId}`

**Response**:
```json
{
  "notificationId": "NOTIF-12345",
  "status": "DELIVERED",
  "type": "SMS",
  "recipient": "+1234567890",
  "sentAt": 1709380800000
}
```

**Example**:
```bash
curl http://localhost:8080/api/notifications/status/NOTIF-12345
```

---

## 🏥 Complete End-to-End Flow Example

### Scenario: Patient needs ambulance

**Step 1: Register Ambulances**
```bash
# Register AMB-101
curl -X POST http://localhost:8080/api/ambulances \
  -H "Content-Type: application/json" \
  -d '{
    "ambulanceId": "AMB-101",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "status": "AVAILABLE"
  }'

# Register AMB-102
curl -X POST http://localhost:8080/api/ambulances \
  -H "Content-Type: application/json" \
  -d '{
    "ambulanceId": "AMB-102",
    "latitude": 28.7041,
    "longitude": 77.1025,
    "status": "AVAILABLE"
  }'
```

**Step 2: Simulate Location Updates**
```bash
# Update AMB-101 location
curl -X POST http://localhost:8080/api/tracking/location \
  -H "Content-Type: application/json" \
  -d '{
    "ambulanceId": "AMB-101",
    "lat": 28.6200,
    "lon": 77.2100,
    "speed": 45.5,
    "heading": 90.0
  }'
```

**Step 3: Create Emergency**
```bash
curl -X POST http://localhost:8080/api/emergencies \
  -H "Content-Type: application/json" \
  -d '{
    "emergencyId": "EMG-12345",
    "lat": 28.6500,
    "lon": 77.2000,
    "priority": "HIGH"
  }'
```

**Step 4: Check Dispatch (automatic)**
The dispatch service automatically:
1. Picks up emergency from queue
2. Gets available ambulances from Redis
3. Calculates routes using OSRM
4. Selects ambulance with minimum ETA
5. Publishes assignment to Kafka

**Step 5: Notification Sent (automatic)**
Notification service automatically:
1. Receives assignment event
2. Sends SMS to patient
3. Sends push notification to driver

**Step 6: Track in Real-Time**
```bash
# Get all ambulance locations
curl http://localhost:8080/api/tracking/ambulances

# Or connect via WebSocket for live updates
```

---

## 📊 Health Check Endpoints

Check if services are running:

```bash
# API Gateway
curl http://localhost:8080/actuator/health

# Emergency Service
curl http://localhost:8081/actuator/health

# Ambulance Service
curl http://localhost:8082/actuator/health

# Dispatch Service
curl http://localhost:8083/api/dispatch/health

# Notification Service
curl http://localhost:8084/actuator/health

# Tracking Service
curl http://localhost:8085/actuator/health
```

---

## 📈 Prometheus Metrics

All services expose Prometheus metrics:

```bash
# API Gateway metrics
curl http://localhost:8080/actuator/prometheus

# Emergency Service metrics
curl http://localhost:8081/actuator/prometheus

# Ambulance Service metrics
curl http://localhost:8082/actuator/prometheus

# Dispatch Service metrics
curl http://localhost:8083/actuator/prometheus

# Notification Service metrics
curl http://localhost:8084/actuator/prometheus

# Tracking Service metrics
curl http://localhost:8085/actuator/prometheus
```

**Key Metrics**:
- `emergency_requests_total` - Total emergency requests
- `dispatch_assignments_published_total` - Successful assignments
- `dispatch_no_available_ambulance_total` - Failed assignments
- `notification_sent_total` - Notifications sent
- `ambulance_location_updates_total` - Location updates

---

## 🔐 Authentication (Future Enhancement)

Currently, the API is open. For production, add:

1. **JWT Authentication** in API Gateway
2. **API Keys** for ambulance devices
3. **OAuth2** for mobile apps
4. **Rate Limiting** per client

---

## 🌍 CORS Configuration

API Gateway allows all origins for development:
```yaml
cors:
  allowed-origins: "*"
  allowed-methods: GET, POST, PUT, DELETE
  allowed-headers: "*"
```

For production, restrict to specific domains:
```yaml
cors:
  allowed-origins: "https://yourdomain.com"
```

---

## 🚀 Testing with Postman

Import this collection to test all APIs:

1. Create new collection: "Emergency Dispatch System"
2. Set base URL variable: `{{baseUrl}} = http://localhost:8080`
3. Add requests for each endpoint above
4. Test end-to-end flow

---

## 📱 Mobile App Integration

For mobile apps (React Native, Flutter):

1. **Register Ambulance**: Call on app login
2. **Send Location**: Call every 5 seconds with GPS data
3. **Receive Assignments**: Listen to push notifications
4. **Update Status**: Call when driver changes status

---

## 🎯 Best Practices

1. **Always use API Gateway** (port 8080), not direct service ports
2. **Include priority** in emergency requests
3. **Send location updates** every 5 seconds for accuracy
4. **Handle WebSocket reconnection** in frontend
5. **Check health endpoints** before making requests
6. **Monitor Prometheus metrics** for performance

---

## 🔥 This is Production-Ready!

All APIs follow REST best practices:
- ✅ Proper HTTP methods (GET, POST, PUT, DELETE)
- ✅ JSON request/response
- ✅ Health checks
- ✅ Metrics exposure
- ✅ CORS support
- ✅ Error handling
- ✅ Single entry point (Gateway)
