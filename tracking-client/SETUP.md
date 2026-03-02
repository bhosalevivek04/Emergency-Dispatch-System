# 🚀 Tracking Client Setup Guide

## Quick Start (Development)

### 1. Install Dependencies
```bash
cd tracking-client
npm install
```

### 2. Start Backend Services
Make sure your tracking-service is running on port 8085:
```bash
cd ../tracking-service
./mvnw spring-boot:run
```

Or use Docker Compose for all services:
```bash
cd ..
docker-compose up kafka redis tracking-service
```

### 3. Start React App
```bash
npm start
```

Visit: `http://localhost:3001` (Note: Port 3001 to avoid conflict with Grafana on 3000)

## Features

### 📍 Live Tracking Mode
- Real-time WebSocket connection to tracking-service
- Smooth animated marker movement
- Connection status indicator
- Last update timestamp

### 🗺️ Route Planning Mode
- OSRM-based route calculation
- Distance and ETA display
- Ambulance and hospital markers
- Interactive route visualization

## Testing the System

### 1. Start All Services
```bash
docker-compose up -d
```

### 2. Send Test Location Data

Using curl:
```bash
curl -X POST http://localhost:8085/api/location \
  -H "Content-Type: application/json" \
  -d '{
    "ambulanceId": "AMB001",
    "latitude": 18.5204,
    "longitude": 73.8567,
    "timestamp": "2024-01-01T10:00:00"
  }'
```

Using PowerShell:
```powershell
$body = @{
    ambulanceId = "AMB001"
    latitude = 18.5204
    longitude = 73.8567
    timestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8085/api/location" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

### 3. Simulate Movement
```powershell
# Simulate ambulance moving
$locations = @(
    @{lat=18.5204; lng=73.8567},
    @{lat=18.5214; lng=73.8557},
    @{lat=18.5224; lng=73.8547},
    @{lat=18.5234; lng=73.8537}
)

foreach ($loc in $locations) {
    $body = @{
        ambulanceId = "AMB001"
        latitude = $loc.lat
        longitude = $loc.lng
        timestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
    } | ConvertTo-Json
    
    Invoke-RestMethod -Uri "http://localhost:8085/api/location" `
        -Method Post `
        -ContentType "application/json" `
        -Body $body
    
    Start-Sleep -Seconds 2
}
```

## Docker Deployment

### Build and Run
```bash
cd tracking-client
docker build -t tracking-client .
docker run -p 3001:80 tracking-client
```

### With Docker Compose
```bash
cd ..
docker-compose up tracking-client
```

Visit: `http://localhost:3001`

## Configuration

### Environment Variables (.env)
```env
REACT_APP_WS_URL=http://localhost:8085/ws
REACT_APP_API_URL=http://localhost:8085/api
PORT=3000
```

### Production Configuration
For production, update the WebSocket URL in `TrackingMap.jsx`:
```javascript
const socket = new SockJS(process.env.REACT_APP_WS_URL || "http://localhost:8085/ws");
```

## Troubleshooting

### WebSocket Connection Failed
- Ensure tracking-service is running on port 8085
- Check CORS configuration in tracking-service
- Verify WebSocket endpoint: `http://localhost:8085/ws`

### Map Not Loading
- Check internet connection (OpenStreetMap tiles)
- Verify Leaflet CSS is imported
- Check browser console for errors

### No Location Updates
- Verify Kafka is running
- Check Redis connection
- Ensure location data is being published to Kafka topic
- Check tracking-service logs

## Architecture Flow

```
1. Driver App → Kafka (location-updates topic)
2. Tracking Service → Consumes from Kafka
3. Tracking Service → Stores in Redis
4. Tracking Service → Broadcasts via WebSocket
5. React Client → Receives via STOMP
6. Leaflet → Animates marker position
```

## Performance Tips

1. **Smooth Animation**: Adjust interpolation steps in `smoothMove()`
2. **Update Frequency**: Control Kafka producer rate
3. **Map Performance**: Use marker clustering for multiple ambulances
4. **WebSocket**: Implement reconnection logic for production

## Next Steps

- [ ] Add multiple ambulance tracking
- [ ] Implement geofencing alerts
- [ ] Add historical route playback
- [ ] Integrate with dispatch-service
- [ ] Add real-time ETA calculation
- [ ] Implement driver authentication
- [ ] Add offline support with service workers

## Resume Impact

This project demonstrates:
- Event-driven architecture (Kafka)
- Real-time data streaming (WebSocket/STOMP)
- Distributed caching (Redis)
- Modern React development
- Microservices design
- Docker containerization
- Full-stack integration

Perfect for showcasing 3+ years of backend/full-stack experience.
