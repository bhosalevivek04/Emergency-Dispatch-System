# 🗺️ Real-Time Tracking Client Integration

## Overview

The tracking client is a React-based web application that provides real-time visualization of ambulance locations using Leaflet maps and WebSocket connections.

## Architecture Integration

```
┌─────────────────────────────────────────────────────────┐
│                   Emergency Dispatch System              │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Ambulance Service → Kafka → Tracking Service           │
│         ↓                          ↓                     │
│    Location Events            Redis Cache               │
│                                    ↓                     │
│                            WebSocket (STOMP)             │
│                                    ↓                     │
│                         ┌──────────────────┐            │
│                         │ Tracking Client  │            │
│                         │   (React App)    │            │
│                         │                  │            │
│                         │  - Leaflet Map   │            │
│                         │  - Live Markers  │            │
│                         │  - Route Planning│            │
│                         └──────────────────┘            │
└─────────────────────────────────────────────────────────┘
```

## Features

### 1. Real-Time Location Tracking
- WebSocket connection to tracking-service
- Smooth animated marker transitions
- Connection status monitoring
- Last update timestamps

### 2. Route Visualization
- OSRM-based route planning
- Distance and ETA calculation
- Interactive route display
- Ambulance and hospital markers

### 3. Multi-Ambulance Support
- Concurrent tracking of multiple ambulances
- Individual marker management
- Status panel with all active ambulances
- Performance-optimized rendering

## Quick Start

### Development Mode

1. **Start Backend Services**
```bash
docker-compose up -d kafka redis tracking-service
```

2. **Install Dependencies**
```bash
cd tracking-client
npm install
```

3. **Start Development Server**
```bash
npm start
```

Access at: http://localhost:3000

### Production Mode (Docker)

```bash
docker-compose up -d tracking-client
```

Access at: http://localhost:3001

## Integration with Existing System

### 1. Ambulance Service Integration

The ambulance service already publishes location updates to Kafka. The tracking service consumes these events:

**Kafka Topic**: `ambulance-location-updates`

**Event Format**:
```json
{
  "ambulanceId": "AMB001",
  "latitude": 18.5204,
  "longitude": 73.8567,
  "speed": 45,
  "heading": 90,
  "timestamp": "2024-01-01T10:00:00"
}
```

### 2. Tracking Service

The tracking service:
- Consumes location events from Kafka
- Caches latest positions in Redis
- Broadcasts updates via WebSocket

**WebSocket Endpoint**: `ws://localhost:8085/ws`
**STOMP Topic**: `/topic/location`

### 3. React Client

The client:
- Connects to WebSocket endpoint
- Subscribes to STOMP topic
- Renders markers on Leaflet map
- Animates position changes

## API Endpoints

### REST API

```bash
# Get latest location for specific ambulance
GET http://localhost:8085/api/location/{ambulanceId}

# Get all ambulance locations
GET http://localhost:8085/api/location/all

# Manually update location (for testing)
POST http://localhost:8085/api/location
Content-Type: application/json

{
  "ambulanceId": "AMB001",
  "latitude": 18.5204,
  "longitude": 73.8567,
  "speed": 45,
  "heading": 90,
  "timestamp": "2024-01-01T10:00:00"
}
```

### WebSocket

```javascript
// Connect to WebSocket
const socket = new SockJS("http://localhost:8085/ws");
const stompClient = Stomp.over(socket);

// Subscribe to location updates
stompClient.connect({}, () => {
  stompClient.subscribe("/topic/location", (message) => {
    const location = JSON.parse(message.body);
    console.log("Location update:", location);
  });
});
```

## Testing

### 1. Manual Testing

Use the provided PowerShell script:
```powershell
.\start-tracking-demo.ps1
```

This will:
- Start all required services
- Launch the React frontend
- Simulate ambulance movement
- Display real-time updates

### 2. API Testing

```bash
# Test single location update
curl -X POST http://localhost:8085/api/location \
  -H "Content-Type: application/json" \
  -d '{
    "ambulanceId": "AMB001",
    "latitude": 18.5204,
    "longitude": 73.8567,
    "speed": 45,
    "heading": 90,
    "timestamp": "2024-01-01T10:00:00"
  }'
```

### 3. Load Testing

Simulate multiple ambulances:
```powershell
# See start-tracking-demo.ps1 for multi-ambulance simulation
```

## Configuration

### Environment Variables

**Backend (tracking-service)**:
```yaml
spring:
  kafka:
    bootstrap-servers: kafka:9092
  data:
    redis:
      host: redis
      port: 6379
server:
  port: 8085
```

**Frontend (tracking-client)**:
```env
REACT_APP_WS_URL=http://localhost:8085/ws
REACT_APP_API_URL=http://localhost:8085/api
PORT=3000
```

### CORS Configuration

Ensure tracking-service allows WebSocket connections:

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000", "http://localhost:3001")
                .withSockJS();
    }
}
```

## Deployment

### Docker Compose

The tracking-client is already integrated into docker-compose.yml:

```yaml
tracking-client:
  build: ./tracking-client
  container_name: tracking-client
  depends_on:
    - tracking-service
  ports:
    - "3001:80"
  restart: always
```

### Production Considerations

1. **HTTPS/WSS**: Use secure WebSocket connections
2. **Load Balancing**: Sticky sessions for WebSocket
3. **CDN**: Serve static assets via CDN
4. **Caching**: Enable browser caching for map tiles
5. **Monitoring**: Add frontend error tracking

## Monitoring

### Metrics to Track

1. **WebSocket Connections**
   - Active connections
   - Connection failures
   - Reconnection attempts

2. **Location Updates**
   - Update frequency
   - Latency from Kafka to client
   - Message loss rate

3. **Frontend Performance**
   - Page load time
   - Map rendering time
   - Memory usage

### Grafana Dashboard

Add panels for:
- Active WebSocket connections
- Location update rate
- Client-side errors
- Map interaction metrics

## Troubleshooting

### WebSocket Connection Failed

**Symptoms**: "🔴 Disconnected" status in UI

**Solutions**:
1. Verify tracking-service is running: `docker ps | grep tracking-service`
2. Check CORS configuration
3. Verify WebSocket endpoint: `curl http://localhost:8085/ws`
4. Check browser console for errors

### No Location Updates

**Symptoms**: Map loads but markers don't move

**Solutions**:
1. Verify Kafka is running: `docker ps | grep kafka`
2. Check Kafka topic: `docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic ambulance-location-updates`
3. Verify Redis: `docker exec -it redis redis-cli GET ambulance:AMB001:location`
4. Check tracking-service logs: `docker logs tracking-service`

### Map Not Loading

**Symptoms**: Blank map area

**Solutions**:
1. Check internet connection (OpenStreetMap tiles)
2. Verify Leaflet CSS is imported
3. Check browser console for tile loading errors
4. Try alternative tile provider

## Performance Optimization

### Backend
- Redis caching reduces database load
- Kafka batching improves throughput
- WebSocket compression reduces bandwidth

### Frontend
- Marker clustering for many ambulances
- Debounced map updates
- Lazy loading of components
- Service worker for offline support

## Future Enhancements

1. **Authentication**: Add JWT-based auth
2. **Geofencing**: Alert when ambulance enters/exits zones
3. **Historical Playback**: Replay past routes
4. **Mobile App**: React Native version
5. **Traffic Integration**: Real-time traffic data
6. **ETA Calculation**: Dynamic arrival time estimates
7. **Driver App**: Mobile app for ambulance drivers
8. **Push Notifications**: Real-time alerts

## Contributing

When adding features to the tracking client:

1. Follow React best practices
2. Add PropTypes or TypeScript
3. Write unit tests
4. Update documentation
5. Test WebSocket reconnection
6. Verify mobile responsiveness

## License

MIT License - See LICENSE file for details
