# Starting Backend Services

To run the Emergency Dispatch System, you need to start these services in order:

## Prerequisites
- PostgreSQL running on port 5432
- Kafka running (via Docker or locally)
- Redis running (via Docker or locally)

## Services to Start (in STS/Eclipse)

### 1. Auth Service (Port 8086) ✅ Already Running
- Right-click on `AuthServiceApplication.java`
- Run As → Spring Boot App

### 2. API Gateway (Port 8080) - REQUIRED
- Right-click on `ApiGatewayApplication.java` 
- Run As → Spring Boot App
- This routes all frontend requests to backend services

### 3. Emergency Service (Port 8081)
- Right-click on `EmergencyServiceApplication.java`
- Run As → Spring Boot App
- Handles emergency creation and status updates

### 4. Ambulance Service (Port 8082)
- Right-click on `AmbulanceServiceApplication.java`
- Run As → Spring Boot App
- Handles fleet management and ambulance tracking

### 5. Dispatch Service (Port 8083)
- Right-click on `DispatchServiceApplication.java`
- Run As → Spring Boot App
- Handles emergency-to-ambulance assignment

### 6. Tracking Service (Port 8084)
- Right-click on `TrackingServiceApplication.java`
- Run As → Spring Boot App
- Handles real-time location updates

### 7. Notification Service (Port 8085) - Optional
- Right-click on `NotificationServiceApplication.java`
- Run As → Spring Boot App
- Handles notifications (optional for basic functionality)

## Quick Start Order

**Minimum services needed for frontend to work:**
1. Auth Service (8086) ✅
2. API Gateway (8080) ⚠️ CRITICAL - Start this first!
3. Emergency Service (8081)
4. Ambulance Service (8082)
5. Dispatch Service (8083)

## Verify Services are Running

Check these URLs in your browser:
- Auth Service: http://localhost:8086/actuator/health
- API Gateway: http://localhost:8080/actuator/health
- Emergency Service: http://localhost:8081/actuator/health
- Ambulance Service: http://localhost:8082/actuator/health

## Frontend

Once services are running:
```bash
cd tracking-client
npm start
```

Then login at http://localhost:3002 with:
- Admin: `admin` / `admin123`
- Dispatcher: `dispatcher` / `dispatcher123`
- Driver: `driver1` / `driver123`

## Troubleshooting

### 404 Errors
- Make sure API Gateway (port 8080) is running
- Check that the specific service for that endpoint is running

### 401 Unauthorized on WebSocket
- Make sure you're logged in
- Check that Auth Service is running
- Verify JWT token is being sent

### Database Connection Errors
- Ensure PostgreSQL is running
- Check credentials in `.env` file match database
- Default: `dispatch_user` / `dispatch_password`

### Kafka Connection Errors
- Start Kafka using Docker Compose:
  ```bash
  docker-compose up -d kafka zookeeper
  ```
