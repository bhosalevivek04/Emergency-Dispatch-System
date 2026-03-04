# Verify System State After Ambulance Service Restart

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     VERIFY AMBULANCE SERVICE RESTART                  ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan

Write-Host "`nHave you restarted ambulance-service in STS? (y/n): " -NoNewline -ForegroundColor Yellow
$response = Read-Host

if ($response -ne "y") {
    Write-Host "`n⚠ Please restart ambulance-service first!" -ForegroundColor Red
    Write-Host "`nSteps:" -ForegroundColor Yellow
    Write-Host "  1. Stop ambulance-service in STS Console" -ForegroundColor White
    Write-Host "  2. Right-click ambulance-service project" -ForegroundColor White
    Write-Host "  3. Run As → Spring Boot App" -ForegroundColor White
    Write-Host "  4. Wait for 'Started AmbulanceServiceApplication'" -ForegroundColor White
    Write-Host "  5. Run this script again`n" -ForegroundColor White
    exit 1
}

Write-Host "`n[1/3] Checking Service Health..." -ForegroundColor Yellow

try {
    $health = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method Get -TimeoutSec 5
    if ($health.status -eq "UP") {
        Write-Host "  ✓ Ambulance service is UP" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Ambulance service health: $($health.status)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "  ✗ Cannot reach ambulance service on port 8082" -ForegroundColor Red
    Write-Host "  Make sure it's running in STS!" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n[2/3] Checking Redis Ambulance Status..." -ForegroundColor Yellow

# Check Redis directly using redis-cli
try {
    $amb101 = redis-cli GET "ambulance:AMB-101:status"
    $amb102 = redis-cli GET "ambulance:AMB-102:status"
    $amb103 = redis-cli GET "ambulance:AMB-103:status"
    
    $amb101Active = redis-cli GET "ambulance:AMB-101:activeEmergencyId"
    $amb102Active = redis-cli GET "ambulance:AMB-102:activeEmergencyId"
    $amb103Active = redis-cli GET "ambulance:AMB-103:activeEmergencyId"
    
    Write-Host "`n  AMB-101:" -ForegroundColor White
    Write-Host "    Status: $amb101" -ForegroundColor $(if ($amb101 -eq "AVAILABLE") { "Green" } else { "Red" })
    Write-Host "    Active Emergency: $amb101Active" -ForegroundColor Gray
    
    Write-Host "`n  AMB-102:" -ForegroundColor White
    Write-Host "    Status: $amb102" -ForegroundColor $(if ($amb102 -eq "AVAILABLE") { "Green" } else { "Red" })
    Write-Host "    Active Emergency: $amb102Active" -ForegroundColor Gray
    
    Write-Host "`n  AMB-103:" -ForegroundColor White
    Write-Host "    Status: $amb103" -ForegroundColor $(if ($amb103 -eq "AVAILABLE") { "Green" } else { "Red" })
    Write-Host "    Active Emergency: $amb103Active" -ForegroundColor Gray
    
    $availableCount = 0
    if ($amb101 -eq "AVAILABLE") { $availableCount++ }
    if ($amb102 -eq "AVAILABLE") { $availableCount++ }
    if ($amb103 -eq "AVAILABLE") { $availableCount++ }
    
    Write-Host "`n  Total Available: $availableCount / 3" -ForegroundColor $(if ($availableCount -eq 3) { "Green" } else { "Yellow" })
    
    if ($availableCount -eq 3) {
        Write-Host "`n  ✓ All ambulances are AVAILABLE!" -ForegroundColor Green
        Write-Host "  ✓ No active emergencies!" -ForegroundColor Green
        Write-Host "`n  🎉 Ready for demo!" -ForegroundColor Green
    } else {
        Write-Host "`n  ⚠ Some ambulances still not available" -ForegroundColor Yellow
        Write-Host "  This might be because:" -ForegroundColor Gray
        Write-Host "    - Service just started (wait 5 seconds)" -ForegroundColor Gray
        Write-Host "    - Auto-heal hasn't run yet (runs every 60 seconds)" -ForegroundColor Gray
        Write-Host "    - Need to run FINAL-COMPLETE-RESET.ps1" -ForegroundColor Gray
    }
    
} catch {
    Write-Host "  ✗ Cannot check Redis (is redis-cli installed?)" -ForegroundColor Red
    Write-Host "  Trying via dispatch service..." -ForegroundColor Yellow
}

Write-Host "`n[3/3] Checking Dispatch View..." -ForegroundColor Yellow

try {
    $debug = Invoke-RestMethod -Uri "http://localhost:8083/debug/ambulance-status" -Method Get
    $metrics = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.ambulances.available.count" -Method Get
    
    Write-Host "`n  Dispatch sees:" -ForegroundColor White
    Write-Host "    AMB-101: $($debug.redisStatus.'AMB-101') - Available: $($debug.dispatchView.'AMB-101')" -ForegroundColor $(if ($debug.dispatchView.'AMB-101') { "Green" } else { "Red" })
    Write-Host "    AMB-102: $($debug.redisStatus.'AMB-102') - Available: $($debug.dispatchView.'AMB-102')" -ForegroundColor $(if ($debug.dispatchView.'AMB-102') { "Green" } else { "Red" })
    Write-Host "    AMB-103: $($debug.redisStatus.'AMB-103') - Available: $($debug.dispatchView.'AMB-103')" -ForegroundColor $(if ($debug.dispatchView.'AMB-103') { "Green" } else { "Red" })
    
    Write-Host "`n  Available count: $($metrics.measurements[0].value)" -ForegroundColor Cyan
    
    if ($metrics.measurements[0].value -eq 3) {
        Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Green
        Write-Host "║              ✓ SYSTEM READY FOR DEMO!                ║" -ForegroundColor Green
        Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Green
        
        Write-Host "`nRun the demo now:" -ForegroundColor Yellow
        Write-Host "  ./FINAL-WORKING-DEMO.ps1" -ForegroundColor Cyan
        Write-Host "`nExpected result:" -ForegroundColor Yellow
        Write-Host "  ✓ 3 emergencies assigned immediately (HIGH priority)" -ForegroundColor Green
        Write-Host "  ⏳ 2 emergencies waiting in queue (MEDIUM priority)" -ForegroundColor Yellow
        Write-Host ""
    } else {
        Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Yellow
        Write-Host "║           ⚠ NOT ALL AMBULANCES AVAILABLE             ║" -ForegroundColor Yellow
        Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
        
        Write-Host "`nOptions:" -ForegroundColor Yellow
        Write-Host "  1. Wait 60 seconds for auto-heal to run" -ForegroundColor White
        Write-Host "  2. Run: ./FINAL-COMPLETE-RESET.ps1" -ForegroundColor White
        Write-Host "  3. Restart ALL services in STS" -ForegroundColor White
        Write-Host ""
    }
    
} catch {
    Write-Host "  ✗ Cannot check dispatch service" -ForegroundColor Red
}

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                  VERIFICATION COMPLETE                ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

