# Debug Dispatch Issue - Step by Step

## Current Situation
- Dispatch knows about 3 ambulances (in memory cache)
- Redis shows all 3 as AVAILABLE
- But dispatch reports 0 available ambulances
- This suggests `isAvailableInRedis()` is returning false

## Changes Made

I've added detailed debug logging to `dispatch-service/src/main/java/com/vivek/dispatch/service/DispatchEngine.java`:

1. **isAvailableInRedis()** - Now logs every status check:
   ```
   Checked ambulance availability ambulanceId=AMB-101 rawStatus='AVAILABLE' status='AVAILABLE' available=true
   ```

2. **findNearestAvailable()** - Now logs each ambulance checked:
   ```
   Finding nearest ambulance for emergency emergencyId=EMG-XXX totalAmbulances=3
   Checking ambulance ambulanceId=AMB-101 available=true
   ```

## Steps to Debug

### 1. Restart dispatch-service in STS
```
- Stop dispatch-service in STS
- Right-click dispatch-service project
- Run As → Spring Boot App
- Wait for startup on port 8083
```

### 2. Enable DEBUG logging (Optional but Recommended)
Add this to `dispatch-service/src/main/resources/application.yml`:
```yaml
logging:
  level:
    com.vivek.dispatch.service.DispatchEngine: DEBUG
```

Then restart dispatch-service.

### 3. Run the test script
```powershell
./trigger-dispatch-and-check.ps1
```

### 4. Watch the STS Console

Look for these log messages in dispatch-service console:

**When emergency arrives:**
```
Finding nearest ambulance for emergency emergencyId=EMG-XXX totalAmbulances=3
```

**For each ambulance:**
```
Checking ambulance ambulanceId=AMB-101 available=?
Checked ambulance availability ambulanceId=AMB-101 rawStatus='?' status='?' available=?
```

**Result:**
```
Found nearest ambulance emergencyId=EMG-XXX ambulanceId=AMB-101 eta=123s
Assignment published emergencyId=EMG-XXX ambulanceId=AMB-101
```

OR

```
No nearest ambulance found emergencyId=EMG-XXX checkedAmbulances=3
No ambulance available emergencyId=EMG-XXX knownAmbulances=3 availableCount=0.0
```

## What to Look For

### If logs show `available=false`:
The `rawStatus` and `status` values will tell us why:
- If `rawStatus` is not "AVAILABLE", something is changing the status
- If `status` is something unexpected, there's a parsing issue
- If `rawStatus` is empty/null, the Redis key doesn't exist

### If logs show `available=true` but still no assignment:
- Check if OSRM service is working (route calculation)
- Check if there's an exception during assignment
- Check if Kafka publish is failing

### If no logs appear at all:
- Dispatch service isn't processing the emergency
- Check if emergency reached dispatch queue in Redis
- Check Kafka `emergency-topic` for messages

## Quick Checks

### Check if emergency reached dispatch:
```powershell
docker exec redis redis-cli LLEN "dispatch:queue:HIGH"
```

### Check Kafka emergency topic:
```powershell
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic emergency-topic --from-beginning --max-messages 1 --timeout-ms 2000
```

### Check dispatch consumer is running:
```powershell
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group dispatch-group --describe
```

## Expected Outcome

After restart with debug logging, you should see:
```
Finding nearest ambulance for emergency emergencyId=EMG-XXX totalAmbulances=3
Checking ambulance ambulanceId=AMB-101 available=true
Checked ambulance availability ambulanceId=AMB-101 rawStatus='AVAILABLE' status='AVAILABLE' available=true
Checking ambulance ambulanceId=AMB-102 available=true
Checked ambulance availability ambulanceId=AMB-102 rawStatus='AVAILABLE' status='AVAILABLE' available=true
Checking ambulance ambulanceId=AMB-103 available=true
Checked ambulance availability ambulanceId=AMB-103 rawStatus='AVAILABLE' status='AVAILABLE' available=true
Found nearest ambulance emergencyId=EMG-XXX ambulanceId=AMB-101 eta=234s
Assignment published emergencyId=EMG-XXX ambulanceId=AMB-101
```

## Next Steps

1. Restart dispatch-service
2. Run `./trigger-dispatch-and-check.ps1`
3. Copy the relevant logs from STS console
4. Share the logs so we can see exactly what dispatch is seeing

The debug logs will reveal the exact issue!
