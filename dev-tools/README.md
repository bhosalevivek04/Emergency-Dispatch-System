# Development Tools

This directory contains testing, debugging, and development utilities for the Emergency Dispatch System.

## 📁 Directory Structure

```
dev-tools/
├── docs/              # Development documentation
├── *.ps1              # PowerShell test/debug scripts
└── README.md          # This file
```

## 🚀 Quick Start

### Run Complete Demo

```powershell
./FINAL-WORKING-DEMO.ps1
```

Creates 5 emergencies and demonstrates automatic dispatch with priority queueing.

### Test Single Emergency

```powershell
./create-test-emergency.ps1
```

### Reset System

```powershell
# Force reset ambulances
./force-reset-ambulances.ps1

# Complete system reset (nuclear option)
./FINAL-COMPLETE-RESET.ps1
```

## 🔧 Available Scripts

### Demo & Testing

| Script | Purpose |
|--------|---------|
| `FINAL-WORKING-DEMO.ps1` | Complete system demo (5 emergencies) |
| `create-test-emergency.ps1` | Create single test emergency |
| `refresh-ambulances.ps1` | Send ambulance locations to Kafka |
| `test-authorization.ps1` | Test service-level authorization |

### Diagnostics

| Script | Purpose |
|--------|---------|
| `verify-after-restart.ps1` | Verify system readiness |
| `show-ambulance-missions.ps1` | Show active missions and queue |
| `diagnose-ambulance-status.ps1` | Debug ambulance status issues |
| `FINAL-DEBUG.ps1` | Comprehensive system diagnostics |

### Reset & Fix

| Script | Purpose |
|--------|---------|
| `force-reset-ambulances.ps1` | Force reset all ambulances to AVAILABLE |
| `quick-fix-now.ps1` | Automated fix with step-by-step guidance |
| `COMPLETE-FIX.ps1` | Partial system reset |
| `FINAL-COMPLETE-RESET.ps1` | Nuclear reset (FLUSHALL Redis) |

## 📚 Documentation

See `docs/` directory for detailed documentation:

### Quick Start Guides
- `START-HERE.md` - Navigation hub
- `ACTION-PLAN.md` - Step-by-step troubleshooting
- `QUICK-FIX-GUIDE.md` - Quick reference

### Technical Documentation
- `FINAL-SOLUTION.md` - Complete technical explanation
- `VISUAL-SUMMARY.md` - Visual diagrams and flows
- `README-DEMO.md` - Complete demo guide

### Troubleshooting
- `RESTART-AMBULANCE-SERVICE.md` - Service restart procedures
- `PROPER-RESTART-PROCEDURE.md` - Why restarts work/don't work
- `IMMEDIATE-ACTION.md` - Emergency fixes

## 🎯 Common Tasks

### Run Full Demo

```powershell
# 1. Verify services are running
./verify-after-restart.ps1

# 2. Run demo
./FINAL-WORKING-DEMO.ps1
```

Expected output:
```
✓ Assigned: 3  (First 3 HIGH priority emergencies)
⏳ Pending: 2  (MEDIUM priority emergencies waiting)
🎉 SUCCESS! Automatic dispatch is working!
```

### Fix "No Ambulances Available"

```powershell
# Quick automated fix
./quick-fix-now.ps1

# Or manual steps
./force-reset-ambulances.ps1
# Then restart ambulance-service in STS
./verify-after-restart.ps1
```

### Debug Dispatch Issues

```powershell
# Show what's happening
./show-ambulance-missions.ps1

# Full diagnostics
./FINAL-DEBUG.ps1

# Check specific status
./diagnose-ambulance-status.ps1
```

### Test Authorization

```powershell
./test-authorization.ps1
```

Tests all service-level authorization rules (12 test cases).

## 🔍 Understanding the Scripts

### FINAL-WORKING-DEMO.ps1

Creates a complete demo scenario:
1. Checks all services are running
2. Broadcasts ambulance locations
3. Checks ambulance availability
4. Creates 5 emergencies (3 HIGH, 2 MEDIUM)
5. Waits for automatic dispatch
6. Shows results and metrics

### force-reset-ambulances.ps1

Forces all ambulances to AVAILABLE state:
1. Deletes all Redis keys for each ambulance
2. Sets status to AVAILABLE
3. Resets version to 0
4. Clears active emergency assignments

### verify-after-restart.ps1

Verifies system is ready for demo:
1. Checks ambulance-service health
2. Checks Redis ambulance status
3. Checks dispatch view
4. Reports availability count

### show-ambulance-missions.ps1

Diagnostic tool showing:
1. Current status of each ambulance
2. Active emergency assignments
3. Last update timestamps
4. Emergency queue depth

## 🐛 Troubleshooting

### Script Execution Policy

If you get "execution policy" errors:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Redis CLI Not Found

Install Redis CLI or use Docker:

```bash
docker exec -it redis redis-cli
```

### Services Not Running

Check services in Spring Tool Suite (STS):
- emergency-service (8081)
- ambulance-service (8082)
- dispatch-service (8083)
- tracking-service (8085)

## 📖 Documentation Index

### For Developers
1. `docs/START-HERE.md` - Start here
2. `docs/FINAL-SOLUTION.md` - Technical deep dive
3. `docs/README-DEMO.md` - Complete feature guide

### For Troubleshooting
1. `docs/ACTION-PLAN.md` - Step-by-step fixes
2. `docs/PROPER-RESTART-PROCEDURE.md` - Restart procedures
3. `docs/IMMEDIATE-ACTION.md` - Emergency fixes

### For Understanding
1. `docs/VISUAL-SUMMARY.md` - Visual diagrams
2. `docs/COMPLETE-DEMO.md` - Demo walkthrough

## ⚠️ Important Notes

### Production vs Development

These tools are for **development and testing only**. Do not use in production:

- Scripts directly manipulate Redis state
- No authentication/authorization checks
- Hardcoded test data
- Force resets bypass safety mechanisms

### Auto-Heal Mechanism

The system has an auto-heal mechanism that runs every 60 seconds:
- Clears orphan assignments (no activeEmergencyId)
- Resets stale assignments (> 15 minutes)
- Resets stale in-flight (> 30 minutes)

If ambulances are stuck, wait 60 seconds or use force-reset scripts.

### Redis State Management

The ambulance-service maintains in-memory mission state for resilience:
- Survives Redis failures
- Uses `setIfAbsent` to avoid overwriting valid state
- Only auto-heals truly stuck missions

For testing, you may need to force reset Redis state.

## 🚀 Next Steps

1. **Run the demo**: `./FINAL-WORKING-DEMO.ps1`
2. **Read documentation**: `docs/START-HERE.md`
3. **Test features**: Try different scripts
4. **Debug issues**: Use diagnostic tools

## 📞 Need Help?

See documentation in `docs/` directory or check the main README.md in the project root.

---

**Happy Testing! 🚑✨**
