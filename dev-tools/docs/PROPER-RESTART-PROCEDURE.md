# 🔧 Proper Restart Procedure - Why Simple Restart Didn't Work

## What Happened

You restarted ambulance-service, but the ambulances are still ON_ROUTE because:

1. **Redis state persisted** - The old mission data is still in Redis
2. **`setIfAbsent` doesn't overwrite** - The initialization code uses `setIfAbsent`, which only sets values if they don't exist
3. **Auto-heal didn't trigger** - Missions are too recent (< 15 minutes) to be considered "stale"

## The Code Issue

```java
@PostConstruct
public void initializeAmbulances() {
    // This only sets if key doesn't exist!
    redisTemplate.opsForValue().setIfAbsent(statusKey(ambulanceId), "AVAILABLE");
    
    // Auto-heal checks age
    healIfStuck(ambulanceId);  // Won't heal if < 15 minutes old
}
```

## The Solution (3 Options)

### Option 1: Force Reset Redis + Restart (RECOMMENDED)

```powershell
# Step 1: Force reset Redis state
./force-reset-ambulances.ps1

# Step 2: Immediately restart ambulance-service in STS
# (Stop and start within 5 seconds)

# Step 3: Verify
./verify-after-restart.ps1

# Step 4: Run demo
./FINAL-WORKING-DEMO.ps1
```

**Why this works:**
- Deletes all ambulance keys from Redis
- Sets them to AVAILABLE
- Ambulance-service won't restore old state because keys are fresh

### Option 2: Nuclear Reset (GUARANTEED)

```powershell
# Step 1: Complete reset
./FINAL-COMPLETE-RESET.ps1

# Step 2: Restart ALL services in STS
# Stop all: emergency, ambulance, dispatch, tracking
# Start in order: emergency → ambulance → dispatch → tracking

# Step 3: Run demo
./FINAL-WORKING-DEMO.ps1
```

**Why this works:**
- FLUSHALL clears entire Redis
- Resets Kafka offsets
- Complete clean slate

### Option 3: Wait for Auto-Heal (60 seconds)

The auto-heal mechanism runs every 60 seconds and will eventually clear stuck ambulances.

```powershell
# Wait 60 seconds
Start-Sleep -Seconds 60

# Check status
./show-ambulance-missions.ps1

# If still not available, wait another 60 seconds
```

**Why this might not work:**
- Auto-heal only triggers for missions > 15 minutes old (ASSIGNED) or > 30 minutes old (ON_ROUTE)
- Your missions are only a few minutes old

## Recommended: Option 1

This is the fastest and most reliable:

```powershell
# 1. Force reset Redis
./force-reset-ambulances.ps1

# 2. IMMEDIATELY restart ambulance-service in STS
#    (within 5 seconds of running the script)

# 3. Verify
./verify-after-restart.ps1

# 4. Run demo
./FINAL-WORKING-DEMO.ps1
```

## Why You Need to Restart AFTER Force Reset

The ambulance-service might have background threads that periodically update Redis with the old mission state. By:

1. **Force resetting Redis** - Clear all old state
2. **Immediately restarting service** - Kill any background threads
3. **Service starts fresh** - No old mission state in memory

## Understanding the Auto-Heal Thresholds

```java
private static final long STALE_ASSIGNMENT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);
private static final long STALE_IN_FLIGHT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

public void healIfStuck(String ambulanceId) {
    // Only heals if:
    // - ASSIGNED for > 15 minutes
    // - ON_ROUTE for > 30 minutes
    // - ARRIVED for > 30 minutes
    // - Has no activeEmergencyId (orphan)
}
```

Your missions are only a few minutes old, so they won't be auto-healed yet.

## The Root Cause

The ambulance-service is designed to be resilient:
- It maintains mission state to survive Redis failures
- It uses `setIfAbsent` to avoid overwriting valid state
- It only auto-heals truly stuck missions (> 15-30 minutes)

This is **correct production behavior**, but for demo purposes, you need a clean slate.

## Next Steps

Choose your option and execute:

### Quick Fix (Option 1)
```powershell
./force-reset-ambulances.ps1
# Then immediately restart ambulance-service in STS
./verify-after-restart.ps1
./FINAL-WORKING-DEMO.ps1
```

### Guaranteed Fix (Option 2)
```powershell
./FINAL-COMPLETE-RESET.ps1
# Restart ALL services in STS
./FINAL-WORKING-DEMO.ps1
```

### Patient Fix (Option 3)
```powershell
# Wait 15-30 minutes for auto-heal
# OR manually trigger by setting lastUpdated to old timestamp
```

## After This Works

Consider adding a "force reset" endpoint to ambulance-service for testing:

```java
@PostMapping("/admin/reset-fleet")
public void resetFleet() {
    for (String ambulanceId : ambulanceIds) {
        redisTemplate.delete(statusKey(ambulanceId));
        redisTemplate.delete(versionKey(ambulanceId));
        redisTemplate.delete(activeEmergencyKey(ambulanceId));
        redisTemplate.delete(lastUpdatedKey(ambulanceId));
        
        redisTemplate.opsForValue().set(statusKey(ambulanceId), "AVAILABLE");
        redisTemplate.opsForValue().set(versionKey(ambulanceId), "0");
    }
}
```

This would make testing much easier!
