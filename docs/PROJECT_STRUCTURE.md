# Project Structure

## Root Directory

```
Emergency-Dispatch-Service/
│
├── 📁 Services (Microservices)
│   ├── api-gateway/              # API Gateway (port 8080)
│   ├── emergency-service/        # Emergency events (port 8081)
│   ├── dispatch-service/         # Assignment logic (port 8083)
│   ├── ambulance-service/        # Movement simulation (port 8082)
│   ├── tracking-service/         # Location broadcasts (port 8085)
│   └── notification-service/     # Alerts (port 8084)
│
├── 📁 Infrastructure
│   ├── osrm-data/                # OSRM map data (Pune)
│   └── monitoring/               # Monitoring configs
│
├── 📁 Documentation
│   ├── README.md                 # Main documentation
│   ├── COMPLETE_SETUP_GUIDE.md   # Complete setup guide
│   ├── API_DOCUMENTATION.md      # API reference
│   ├── MICROSERVICES_ARCHITECTURE.md  # Architecture details
│   ├── OSRM_SETUP.md            # OSRM configuration
│   ├── HOW_TO_TEST.md           # Testing guide
│   ├── DEPLOYMENT_GUIDE.md      # Deployment guide
│   ├── MONITORING_SETUP.md      # Monitoring setup
│   ├── QUICK_START.md           # Quick reference
│   ├── QUICK_FIX.md             # Common issues
│   ├── DIAGNOSE_AND_FIX.md      # Diagnostics
│   ├── CONTRIBUTING.md          # Contribution guide
│   └── LICENSE                  # License file
│
├── 📁 Docker
│   ├── docker-compose-full.yml   # Complete infrastructure
│   ├── docker-compose-osrm.yml   # OSRM only
│   └── docker-compose.yml        # Legacy compose file
│
├── 📁 Scripts
│   ├── start-infrastructure.bat  # Start all infrastructure
│   ├── start-all-services.bat    # Start all microservices
│   ├── test-simple.bat          # Single ambulance test
│   ├── test-ambulance.ps1       # PowerShell test
│   ├── test-multiple-spaced.bat # Multiple ambulances test
│   └── diagnose-issue.bat       # System diagnostics
│
└── 📁 Other
    ├── .gitignore               # Git ignore rules
    ├── .vscode/                 # VS Code settings
    └── images/                  # Documentation images
```

## Essential Files

### Documentation (Keep)
- ✅ `README.md` - Main project documentation
- ✅ `COMPLETE_SETUP_GUIDE.md` - Complete setup instructions
- ✅ `API_DOCUMENTATION.md` - API reference
- ✅ `MICROSERVICES_ARCHITECTURE.md` - Architecture details
- ✅ `OSRM_SETUP.md` - OSRM setup guide
- ✅ `HOW_TO_TEST.md` - Testing guide
- ✅ `DEPLOYMENT_GUIDE.md` - Deployment instructions
- ✅ `MONITORING_SETUP.md` - Monitoring setup
- ✅ `QUICK_START.md` - Quick reference
- ✅ `QUICK_FIX.md` - Troubleshooting
- ✅ `DIAGNOSE_AND_FIX.md` - Diagnostics
- ✅ `CONTRIBUTING.md` - Contribution guidelines
- ✅ `LICENSE` - License file

### Scripts (Keep)
- ✅ `start-infrastructure.bat` - Start Docker services
- ✅ `start-all-services.bat` - Start all microservices
- ✅ `test-simple.bat` - Quick test
- ✅ `test-ambulance.ps1` - PowerShell test
- ✅ `test-multiple-spaced.bat` - Multiple ambulances
- ✅ `diagnose-issue.bat` - Diagnostics

### Docker (Keep)
- ✅ `docker-compose-full.yml` - Complete infrastructure
- ✅ `docker-compose-osrm.yml` - OSRM only
- ✅ `docker-compose.yml` - Legacy (can be removed if not used)

## Removed Files

### Duplicate Documentation (Removed)
- ❌ `ARCHITECTURE.md` (duplicate of MICROSERVICES_ARCHITECTURE.md)
- ❌ `ARCHITECTURE_VISUAL.md` (duplicate)
- ❌ `COMPLETE_SYSTEM_SUMMARY.md` (duplicate)
- ❌ `START_HERE.md` (duplicate of QUICK_START.md)
- ❌ `DEPLOYMENT.md` (duplicate of DEPLOYMENT_GUIDE.md)
- ❌ `README_TESTING.md` (duplicate of HOW_TO_TEST.md)
- ❌ `README_MICROSERVICES.md` (duplicate)
- ❌ `SUCCESS.md` (temporary test result)
- ❌ `SINGLE_AMBULANCE_TEST.md` (duplicate)
- ❌ `MANUAL_TEST_GUIDE.md` (duplicate)
- ❌ `DISPATCH_INTEGRATION_COMPLETE.md` (temporary)
- ❌ `FINAL_SUMMARY.md` (duplicate)
- ❌ `SYSTEM_FLOW_DIAGRAM.md` (duplicate)
- ❌ `TEST_SIMULATION.md` (duplicate)
- ❌ `MULTIPLE_AMBULANCES_RESULTS.md` (temporary test result)

### Duplicate Scripts (Removed)
- ❌ `test-with-logs.bat` (duplicate)
- ❌ `test-dispatch-integration.bat` (duplicate)
- ❌ `check-dispatch-cache.bat` (duplicate)
- ❌ `check-queue.bat` (duplicate)
- ❌ `run-single-ambulance-test.bat` (duplicate)
- ❌ `simulate-emergencies.ps1` (old simulation)
- ❌ `test-complete-flow.bat` (duplicate)
- ❌ `simulate-route.ps1` (old simulation)
- ❌ `test-multiple-ambulances.bat` (duplicate)
- ❌ `verify-all-emergencies.bat` (duplicate)
- ❌ `test-single-ambulance.bat` (duplicate)
- ❌ `test-with-verification.bat` (duplicate)
- ❌ `simulate-ambulance.ps1` (old simulation)
- ❌ `test-microservices.bat` (duplicate)

### Other (Removed)
- ❌ `logs` (temporary log file)

## Quick Reference

### Start System
```bash
# 1. Start infrastructure
start-infrastructure.bat

# 2. Start services (in separate terminals)
start-all-services.bat
```

### Test System
```bash
# Single ambulance
test-simple.bat

# Multiple ambulances
test-multiple-spaced.bat

# Diagnostics
diagnose-issue.bat
```

### Documentation
- Start with `README.md`
- Setup: `COMPLETE_SETUP_GUIDE.md`
- Testing: `HOW_TO_TEST.md`
- Issues: `QUICK_FIX.md`

## Clean Structure Benefits

✅ **No Duplicates** - Each document has a single purpose
✅ **Clear Organization** - Easy to find what you need
✅ **Essential Only** - Only production-ready files
✅ **Easy Maintenance** - Less clutter, easier updates
✅ **Professional** - Clean, organized project structure
