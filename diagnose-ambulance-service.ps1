# Diagnostic script for ambulance service issues

Write-Host "`n=== Ambulance Service Diagnostics ===" -ForegroundColor Cyan

# Check 1: Is ambulance service running?
Write-Host "`n[Check 1] Testing ambulance service endpoint..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method Get -TimeoutSec 5
    Write-Host "✓ Ambulance service is responding" -ForegroundColor Green
    Write-Host "  Status: $($health.status)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Ambulance service not responding on port 8082" -ForegroundColor Red
    Write-Host "  Error: $_" -ForegroundColor Red
    Write-Host "`nPlease check STS console for ambulance-service errors" -ForegroundColor Yellow
    exit 1
}

# Check 2: Can we connect to Redis?
Write-Host "`n[Check 2] Testing Redis connection..." -ForegroundColor Yellow
try {
    $ping = docker exec redis redis-cli PING
    if ($ping -eq "PONG") {
        Write-Host "✓ Redis is responding" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Redis connection failed" -ForegroundColor Red
    exit 1
}

# Check 3: What's in Redis?
Write-Host "`n[Check 3] Checking Redis contents..." -ForegroundColor Yellow
$allKeys = docker exec redis redis-cli KEYS "*"
if ($allKeys) {
    Write-Host "✓ Redis has keys:" -ForegroundColor Green
    docker exec redis redis-cli KEYS "*" | ForEach-Object {
        Write-Host "  - $_" -ForegroundColor Gray
    }
} else {
    Write-Host "✗ Redis is empty - ambulance service not initializing" -ForegroundColor Red
    Write-Host "`nPossible issues:" -ForegroundColor Yellow
    Write-Host "  1. Check STS console for 'Initializing ambulance fleet' log message" -ForegroundColor Gray
    Write-Host "  2. Check for Redis connection errors in logs" -ForegroundColor Gray
    Write-Host "  3. Check for @PostConstruct initialization errors" -ForegroundColor Gray
    Write-Host "  4. Verify ambulance.fleet.ids property is being read" -ForegroundColor Gray
}

# Check 4: Test Redis write manually
Write-Host "`n[Check 4] Testing manual Redis write..." -ForegroundColor Yellow
docker exec redis redis-cli SET "test:key" "test:value" | Out-Null
$testValue = docker exec redis redis-cli GET "test:key"
if ($testValue -eq "test:value") {
    Write-Host "✓ Redis write/read works" -ForegroundColor Green
    docker exec redis redis-cli DEL "test:key" | Out-Null
} else {
    Write-Host "✗ Redis write/read failed" -ForegroundColor Red
}

# Check 5: Check environment variable
Write-Host "`n[Check 5] Checking fleet configuration..." -ForegroundColor Yellow
$fleetConfig = Get-Content .env | Select-String "AMBULANCE_FLEET_IDS"
if ($fleetConfig) {
    Write-Host "✓ Fleet config in .env: $fleetConfig" -ForegroundColor Green
} else {
    Write-Host "✗ AMBULANCE_FLEET_IDS not found in .env" -ForegroundColor Red
}

Write-Host "`n=== Next Steps ===" -ForegroundColor Cyan
Write-Host "1. Check the STS console for ambulance-service" -ForegroundColor White
Write-Host "2. Look for startup errors or exceptions" -ForegroundColor White
Write-Host "3. Search for 'Initializing ambulance fleet' in the logs" -ForegroundColor White
Write-Host "4. If you see errors, share them for troubleshooting" -ForegroundColor White
