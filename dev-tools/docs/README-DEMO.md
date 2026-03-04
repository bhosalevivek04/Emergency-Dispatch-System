# 🚑 Emergency Dispatch System - Demo Guide

## Quick Start (2 Minutes)

### The Issue
All ambulances are on active missions from previous tests. The ambulance-service has in-memory state that needs to be cleared.

### The Fix
1. **Restart ambulance-service in STS**
2. **Run the demo**

### Detailed Steps

```
┌─────────────────────────────────────────────────────────┐
│  STEP 1: Restart Ambulance Service                     │
└─────────────────────────────────────────────────────────┘

1. Open Spring Tool Suite (STS)
2. Find Console view → ambulance-service tab
3. Click Stop button (■)
4. Right-click ambulance-service project
5. Run As → Spring Boot App
6. Wait for "Started AmbulanceServiceApplication"

┌─────────────────────────────────────────────────────────┐
│  STEP 2: Verify System                                 │
└─────────────────────────────────────────────────────────┘

./verify-after-restart.ps1

Expected output:
  ✓ Ambulance service is UP
  ✓ All ambulances are AVAILABLE!
  ✓ No active emergencies!
  🎉 Ready for demo!

┌─────────────────────────────────────────────────────────┐
│  STEP 3: Run Demo                                      │
└─────────────────────────────────────────────────────────┘

./FINAL-WORKING-DEMO.ps1

Expected output:
  ✓ Assigned: 3
  ⏳ Pending: 2
  🎉 SUCCESS! Automatic dispatch is working!
```

## What the Demo Does

### Creates 5 Emergencies
1. **Koregaon Park** (HIGH) - 18.5204, 73.8567
2. **Shivajinagar** (HIGH) - 18.5314, 73.8446
3. **Kothrud** (HIGH) - 18.5074, 73.8077
4. **Deccan** (MEDIUM) - 18.5167, 73.8422
5. **Viman Nagar** (MEDIUM) - 18.5679, 73.9143

### Automatic Dispatch
- **Second 1**: EMG-1 (HIGH) → AMB-101 (nearest)
- **Second 2**: EMG-2 (HIGH) → AMB-102 (nearest)
- **Second 3**: EMG-3 (HIGH) → AMB-103 (nearest)
- **Second 4**: EMG-4 (MEDIUM) → waiting (no ambulances)
- **Second 5**: EMG-5 (MEDIUM) → waiting (no ambulances)

### Result
```
✓ 3 HIGH priority emergencies assigned immediately
⏳ 2 MEDIUM priority emergencies waiting in queue
🎉 Automatic dispatch working perfectly!
```

## Available Scripts

### Main Demo Scripts

| Script | Purpose | When to Use |
|--------|---------|-------------|
| `FINAL-WORKING-DEMO.ps1` | Complete system demo (5 emergencies) | Main demo |
| `simulate-emergencies.ps1` | Large-scale simulation (8 emergencies) | Stress test |
| `create-test-emergency.ps1` | Single emergency test | Quick test |

### Diagnostic Scripts

| Script | Purpose | Output |
|--------|---------|--------|
| `verify-after-restart.ps1` | Check system readiness | Service health, Redis state, availability |
| `show-ambulance-missions.ps1` | Show active missions | Status, active emergencies, queue depth |
| `diagnose-ambulance-status.ps1` | Debug status issues | Redis vs dispatch view comparison |
| `FINAL-DEBUG.ps1` | Comprehensive diagnostics | Full system state |

### Utility Scripts

| Script | Purpose |
|--------|---------|
| `refresh-ambulances.ps1` | Send ambulance locations to Kafka |
| `FINAL-COMPLETE-RESET.ps1` | Nuclear reset (FLUSHALL Redis) |
| `COMPLETE-FIX.ps1` | Partial reset |

## System Architecture

```
┌──────────────┐
│   Frontend   │
│  (React UI)  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ API Gateway  │  ← Rate Limiting (Redis)
│   (8080)     │  ← JWT Validation
└──────┬───────┘
       │
       ├─────────────────────────────────────┐
       │                                     │
       ▼                                     ▼
┌──────────────┐                    ┌──────────────┐
│  Emergency   │                    │   Tracking   │
│   Service    │                    │   Service    │
│   (8081)     │                    │   (8085)     │
└──────┬───────┘                    └──────┬───────┘
       │                                   │
       │ Kafka: emergency-topic            │ Kafka: ambulance-location-topic
       │                                   │
       ▼                                   ▼
┌──────────────────────────────────────────────────┐
│              Dispatch Service (8083)             │
│  • Consumes emergency events                     │
│  • Consumes ambulance locations                  │
│  • Finds nearest available ambulance             │
│  • Publishes assignment events                   │
│  • Priority queue: HIGH → MEDIUM → LOW           │
│  • Processes every 1 second                      │
└──────────────────────┬───────────────────────────┘
                       │
                       │ Kafka: ambulance-assigned-topic
                       │
                       ▼
                ┌──────────────┐
                │  Ambulance   │
                │   Service    │
                │   (8082)     │
                │  • FSM       │
                │  • Missions  │
                └──────────────┘
```

## State Flow

```
Emergency Created
    │
    ▼
Queued by Priority (Redis)
    │
    ▼
Dispatch Engine (every 1s)
    │
    ├─ Find Nearest Available
    │  (OSRM routing)
    │
    ├─ Acquire Lock
    │  (Redis distributed lock)
    │
    ├─ Publish Assignment
    │  (Kafka)
    │
    └─ Update Status
       (Redis FSM)
    │
    ▼
Ambulance Accepts
    │
    ▼
Status: ASSIGNED → ON_ROUTE → ARRIVED → COMPLETED
    │
    ▼
Ambulance Available Again
```

## Metrics

### Dispatch Service
```
http://localhost:8083/actuator/metrics/dispatch.ambulances.available.count
http://localhost:8083/actuator/metrics/dispatch.ambulances.known.count
http://localhost:8083/actuator/metrics/dispatch.emergencies.queued.total
http://localhost:8083/actuator/metrics/dispatch.assignments.published.total
http://localhost:8083/actuator/metrics/dispatch.no_available_ambulance.total
```

### Emergency Service
```
http://localhost:8081/actuator/metrics/emergency.requests.total
http://localhost:8081/actuator/metrics/emergency.kafka.published.total
```

### Ambulance Service
```
http://localhost:8082/actuator/metrics/ambulance.fsm.transitions.total
http://localhost:8082/actuator/metrics/ambulance.assignment.atomic.applied.total
http://localhost:8082/actuator/metrics/ambulance.auto_heal.total
```

## Debug Endpoints

### Dispatch Service
```bash
# Check ambulance status
curl http://localhost:8083/debug/ambulance-status

# Response:
{
  "redisStatus": {
    "AMB-101": "AVAILABLE",
    "AMB-102": "AVAILABLE",
    "AMB-103": "AVAILABLE"
  },
  "dispatchView": {
    "AMB-101": true,
    "AMB-102": true,
    "AMB-103": true
  }
}
```

### Emergency Service
```bash
# Get emergency status
curl -H "X-User-Username: dispatcher1" \
     -H "X-User-Roles: DISPATCHER" \
     http://localhost:8081/emergency/EMG-XXX

# Response:
{
  "emergencyId": "EMG-XXX",
  "status": "ASSIGNED",
  "assignedAmbulanceId": "AMB-101",
  "priority": "HIGH",
  "latitude": 18.5204,
  "longitude": 73.8567
}
```

## Troubleshooting

### No Ambulances Available

**Symptom**: All emergencies queued, none assigned

**Cause**: Ambulances on active missions from previous tests

**Solution**:
```powershell
# Option 1: Restart ambulance-service in STS
./verify-after-restart.ps1

# Option 2: Wait for auto-heal (60 seconds)
./show-ambulance-missions.ps1

# Option 3: Nuclear reset
./FINAL-COMPLETE-RESET.ps1
```

### Emergency Creation Fails (400)

**Symptom**: `Response status code does not indicate success: 400`

**Cause**: Missing required fields

**Solution**: Ensure request has:
```json
{
  "emergencyId": "EMG-XXX",
  "lat": 18.5204,
  "lon": 73.8567,
  "priority": "HIGH"
}
```

Note: Use `lat`/`lon`, not `latitude`/`longitude`

### Kafka Deserialization Errors

**Symptom**: Logs show deserialization failures

**Cause**: Corrupted messages in Kafka

**Solution**: Already fixed with ErrorHandlingDeserializer
```yaml
spring:
  kafka:
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
```

### Rate Limiting (429)

**Symptom**: `Too Many Requests`

**Cause**: Exceeded rate limit

**Solution**: Wait or adjust limits in `api-gateway/application.yml`

## Production Checklist

- ✅ Microservices architecture
- ✅ Event-driven design (Kafka)
- ✅ Distributed state (Redis)
- ✅ Atomic transitions (Lua scripts)
- ✅ Priority queueing
- ✅ Automatic dispatch
- ✅ Real-time tracking (WebSocket)
- ✅ Authorization (RBAC)
- ✅ Rate limiting
- ✅ Metrics (Prometheus-ready)
- ✅ Health checks
- ✅ Error handling
- ✅ Idempotency
- ✅ Transactional outbox
- ✅ Auto-heal mechanism

## Next Steps for Production

1. **Mission Completion**
   - Timeout-based auto-completion
   - Driver mobile app
   - Admin force-complete API

2. **Monitoring**
   - Grafana dashboards
   - Alerting (PagerDuty)
   - Log aggregation (ELK)

3. **Scaling**
   - Kubernetes deployment
   - Horizontal pod autoscaling
   - Load testing

4. **Features**
   - SMS notifications
   - Real-time dispatcher dashboard
   - Analytics and reporting
   - Multi-city support

## Documentation

- `FINAL-SOLUTION.md` - Complete technical explanation
- `QUICK-FIX-GUIDE.md` - 2-minute quick start
- `RESTART-AMBULANCE-SERVICE.md` - Detailed restart instructions
- `SUCCESS-SUMMARY.md` - System achievements
- `COMPLETE-DEMO.md` - Full demo walkthrough
- `SYSTEM-WORKING.md` - System status

## Support

If you encounter issues:

1. Check logs in STS Console
2. Run diagnostic scripts
3. Verify all services are running
4. Check Redis connectivity
5. Verify Kafka topics

## Congratulations! 🎉

You've built a production-grade emergency dispatch system!

**Now restart ambulance-service and run the demo!** 🚑✨

```powershell
# After restarting ambulance-service in STS:
./verify-after-restart.ps1
./FINAL-WORKING-DEMO.ps1
```
