# Development Tools Organization

This document explains the organization of development and testing tools.

## What Was Moved

All development, testing, and debugging tools have been moved to the `dev-tools/` directory to keep the project root clean for production.

### Scripts Moved to `dev-tools/`

**Demo & Testing:**
- `FINAL-WORKING-DEMO.ps1` - Complete system demo
- `create-test-emergency.ps1` - Single emergency test
- `create-emergency-simple.ps1` - Simple emergency creation
- `refresh-ambulances.ps1` - Send ambulance locations
- `send-ambulances.ps1` - Alternative ambulance sender
- `init-fleet.ps1` - Initialize ambulance fleet
- `test-authorization.ps1` - Authorization testing

**Diagnostics:**
- `verify-after-restart.ps1` - System readiness check
- `show-ambulance-missions.ps1` - Show active missions
- `diagnose-ambulance-status.ps1` - Status diagnostics
- `check-dispatch-state.ps1` - Dispatch state check
- `FINAL-DEBUG.ps1` - Comprehensive diagnostics

**Reset & Fix:**
- `force-reset-ambulances.ps1` - Force reset ambulances
- `quick-fix-now.ps1` - Automated fix
- `reset-ambulances.ps1` - Reset ambulances
- `COMPLETE-FIX.ps1` - Partial reset
- `COMPLETE-RESET.ps1` - Alternative reset
- `FINAL-COMPLETE-RESET.ps1` - Nuclear reset

**Other:**
- `test-emergency.json` - Test data
- `error.txt` - Error logs
- `reference/` - Reference materials

### Documentation Moved to `dev-tools/docs/`

**Quick Start:**
- `START-HERE.md` - Navigation hub
- `ACTION-PLAN.md` - Step-by-step fixes
- `QUICK-FIX-GUIDE.md` - Quick reference

**Technical:**
- `FINAL-SOLUTION.md` - Complete explanation
- `VISUAL-SUMMARY.md` - Visual diagrams
- `README-DEMO.md` - Demo guide
- `COMPLETE-DEMO.md` - Demo walkthrough

**Troubleshooting:**
- `RESTART-AMBULANCE-SERVICE.md` - Restart procedures
- `PROPER-RESTART-PROCEDURE.md` - Restart details
- `IMMEDIATE-ACTION.md` - Emergency fixes

**Other:**
- `DEBUG-DISPATCH.md` - Dispatch debugging
- `DEMO-INSTRUCTIONS.md` - Demo instructions

## What Stayed in Root

### Production Files
- `README.md` - Main project documentation
- `docker-compose.yml` - Production Docker setup
- `docker-compose-full.yml` - Full stack setup
- `docker-compose-osrm.yml` - OSRM setup
- `.env.example` - Environment template
- `generate-keys.ps1` - RSA key generation (needed for setup)

### Production Documentation (`docs/`)
- `API_DOCUMENTATION.md`
- `ARCHITECTURE.md`
- `CONTRIBUTING.md`
- `DATABASE_CONSTRAINTS.md`
- `DISPATCH_ALGORITHM.md`
- `MICROSERVICES_ARCHITECTURE.md`
- `MONITORING_SETUP.md`
- `OPTIMISTIC_LOCKING.md`
- `OUTBOX_PATTERN.md`
- `POSTGRESQL_INTEGRATION.md`
- `PRODUCTION_UPGRADES.md`
- `PROJECT_STRUCTURE.md`
- `REDIS_POSTGRESQL_HYBRID.md`

### Project Files
- `ARCHITECTURE.md` - High-level architecture
- `AUTHORIZATION.md` - Authorization guide
- `DEPLOYMENT.md` - Deployment guide
- `CONTRIBUTING.md` - Contribution guidelines
- `LICENSE` - Project license

## Directory Structure

```
Emergency-Dispatch-Service/
├── ambulance-service/          # Microservice
├── api-gateway/                # API Gateway
├── auth-service/               # Auth service
├── dispatch-service/           # Dispatch service
├── emergency-service/          # Emergency service
├── tracking-service/           # Tracking service
├── notification-service/       # Notification service
├── tracking-client/            # Frontend
├── docs/                       # Production documentation
├── dev-tools/                  # Development tools (THIS)
│   ├── docs/                   # Development documentation
│   ├── reference/              # Reference materials
│   ├── *.ps1                   # Test/debug scripts
│   ├── README.md               # Dev tools guide
│   └── ORGANIZATION.md         # This file
├── init-db/                    # Database initialization
├── monitoring/                 # Monitoring setup
├── osrm-data/                  # OSRM map data
├── images/                     # Project images
├── docker-compose.yml          # Docker setup
├── .env.example                # Environment template
├── README.md                   # Main documentation
└── ...                         # Other production files
```

## Usage

### For Development

```powershell
# Run demo
./dev-tools/FINAL-WORKING-DEMO.ps1

# Debug issues
./dev-tools/show-ambulance-missions.ps1

# Reset system
./dev-tools/force-reset-ambulances.ps1
```

### For Production

The `dev-tools/` directory is kept in the repository for development but is not needed for production deployment. You can:

1. **Keep it** - Useful for debugging production issues
2. **Exclude it** - Add to .dockerignore for production builds
3. **Remove it** - Delete before production deployment (not recommended)

## Git Ignore

The `.gitignore` file has been updated to:
- Keep `dev-tools/` in the repository (commented out)
- Exclude old test files that were moved
- Maintain all production files

## Benefits

### Clean Project Root
- Only production-relevant files in root
- Easy to navigate
- Professional appearance

### Organized Development Tools
- All test scripts in one place
- Clear separation of concerns
- Easy to find tools

### Preserved for Development
- Tools available when needed
- Documentation preserved
- Easy to onboard new developers

## Migration Guide

If you have scripts that reference old paths:

**Before:**
```powershell
./FINAL-WORKING-DEMO.ps1
./verify-after-restart.ps1
```

**After:**
```powershell
./dev-tools/FINAL-WORKING-DEMO.ps1
./dev-tools/verify-after-restart.ps1
```

## Maintenance

### Adding New Dev Tools

Place new development tools in `dev-tools/`:
- Scripts: `dev-tools/*.ps1`
- Documentation: `dev-tools/docs/*.md`
- Test data: `dev-tools/*.json`

### Updating Documentation

- Production docs: `docs/`
- Development docs: `dev-tools/docs/`

## Questions?

See:
- `dev-tools/README.md` - Dev tools guide
- `dev-tools/docs/START-HERE.md` - Quick start
- `README.md` - Main documentation

---

**Organization completed for clean production deployment! 🚀**
