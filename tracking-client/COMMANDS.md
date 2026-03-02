# 🎯 Quick Command Reference

## Installation

```bash
# Install dependencies
npm install

# Clean install (if issues)
rm -rf node_modules package-lock.json
npm install
```

## Development

```bash
# Start development server (port 3002)
npm start

# Start on custom port
PORT=3003 npm start

# Build for production
npm run build

# Run tests
npm test
```

## Backend Services

```bash
# Start all services
docker-compose up -d

# Start specific services
docker-compose up -d kafka redis tracking-service

# Stop all services
docker-compose down

# View logs
docker-compose logs -f tracking-service

# Restart service
docker-compose restart tracking-service
```

## Testing

```bash
# Send single location update
curl -X POST http://localhost:8085/api/location \
  -H "Content-Type: application/json" \
  -d '{
    "ambulanceId": "AMB001",
    "latitude": 18.5204,
    "longitude": 73.8567,
    "speed": 45,
    "timestamp": "2024-01-01T10:00:00"
  }'

# Get latest location
curl http://localhost:8085/api/location/AMB001

# Get all locations
curl http://localhost:8085/api/location/all

# Health check
curl http://localhost:8085/actuator/health
```

## PowerShell Testing

```powershell
# Send location update
$body = @{
    ambulanceId = "AMB001"
    latitude = 18.5204
    longitude = 73.8567
    speed = 45
    timestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8085/api/location" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

# Run demo script
.\start-tracking-demo.ps1
```

## Docker Commands

```bash
# Build image
docker build -t tracking-client .

# Run container
docker run -p 3001:80 tracking-client

# Build and run with compose
docker-compose up -d tracking-client

# View container logs
docker logs -f tracking-client

# Stop container
docker stop tracking-client

# Remove container
docker rm tracking-client
```

## Debugging

```bash
# Check what's on port 3002
netstat -ano | findstr :3002

# Kill process on port (Windows)
# First find PID, then:
taskkill /PID <PID> /F

# Check Kafka topics
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092

# Check Redis
docker exec -it redis redis-cli ping
docker exec -it redis redis-cli GET ambulance:AMB001:location

# Check all services
docker-compose ps
```

## NPM Scripts

```bash
# Start development server
npm start

# Build production bundle
npm run build

# Run tests
npm test

# Eject from Create React App (irreversible!)
npm run eject

# Build Docker image
npm run docker:build

# Run Docker container
npm run docker:run

# Start backend services
npm run services:start

# Stop backend services
npm run services:stop

# View backend logs
npm run services:logs
```

## Git Commands

```bash
# Check status
git status

# Add files
git add .

# Commit changes
git commit -m "Add tracking client"

# Push to remote
git push origin main

# Create new branch
git checkout -b feature/tracking-client

# View logs
git log --oneline
```

## Useful Aliases (Optional)

Add to your PowerShell profile (`$PROFILE`):

```powershell
# Tracking client aliases
function Start-TrackingClient { cd E:\Projects\Emergency-Dispatch-Service\tracking-client; npm start }
function Start-Backend { cd E:\Projects\Emergency-Dispatch-Service; docker-compose up -d kafka redis tracking-service }
function Stop-Backend { cd E:\Projects\Emergency-Dispatch-Service; docker-compose down }
function Send-TestLocation {
    $body = @{
        ambulanceId = "AMB001"
        latitude = 18.5204 + (Get-Random -Minimum -0.01 -Maximum 0.01)
        longitude = 73.8567 + (Get-Random -Minimum -0.01 -Maximum 0.01)
        speed = Get-Random -Minimum 30 -Maximum 60
        timestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "http://localhost:8085/api/location" -Method Post -ContentType "application/json" -Body $body
}

Set-Alias track Start-TrackingClient
Set-Alias backend Start-Backend
Set-Alias stopback Stop-Backend
Set-Alias testloc Send-TestLocation
```

Then use:
```powershell
track        # Start tracking client
backend      # Start backend services
stopback     # Stop backend services
testloc      # Send random test location
```

## Environment Variables

```bash
# View current environment
npm run env

# Set environment variable (PowerShell)
$env:PORT=3003
$env:REACT_APP_WS_URL="http://localhost:8085/ws"

# Set environment variable (CMD)
set PORT=3003
set REACT_APP_WS_URL=http://localhost:8085/ws
```

## Performance

```bash
# Analyze bundle size
npm run build
# Check build/static/js/*.js file sizes

# Run with profiling
npm start -- --profile

# Clear cache
npm cache clean --force
```

## Troubleshooting Commands

```bash
# Fix npm issues
npm cache clean --force
rm -rf node_modules package-lock.json
npm install

# Fix port conflicts
netstat -ano | findstr :3002
taskkill /PID <PID> /F

# Check Node version
node --version

# Check npm version
npm --version

# Update npm
npm install -g npm@latest

# List installed packages
npm list --depth=0

# Check for outdated packages
npm outdated

# Update packages
npm update
```

## Quick Access URLs

```bash
# Open in browser (PowerShell)
Start-Process "http://localhost:3002"  # Frontend
Start-Process "http://localhost:3000"  # Grafana
Start-Process "http://localhost:9090"  # Prometheus
Start-Process "http://localhost:8085/actuator/health"  # Health check
```

## One-Liners

```bash
# Full restart
docker-compose down && docker-compose up -d && cd tracking-client && npm start

# Quick test
curl -X POST http://localhost:8085/api/location -H "Content-Type: application/json" -d '{"ambulanceId":"AMB001","latitude":18.5204,"longitude":73.8567,"timestamp":"2024-01-01T10:00:00"}'

# Check all services
docker-compose ps && curl -s http://localhost:8085/actuator/health | jq

# Build and deploy
npm run build && docker build -t tracking-client . && docker run -d -p 3001:80 tracking-client
```
