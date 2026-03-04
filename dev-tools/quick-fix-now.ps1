# Quick Fix - Force Reset and Instructions

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║              QUICK FIX - FORCE RESET                  ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan

Write-Host "`nThis will:" -ForegroundColor Yellow
Write-Host "  1. Force reset all ambulances to AVAILABLE in Redis" -ForegroundColor White
Write-Host "  2. Guide you to restart ambulance-service" -ForegroundColor White
Write-Host "  3. Verify the system is ready" -ForegroundColor White

Write-Host "`nPress Enter to continue..." -ForegroundColor Gray
Read-Host

Write-Host "`n[STEP 1/4] Force Resetting Redis..." -ForegroundColor Yellow

$ambulances = @("AMB-101", "AMB-102", "AMB-103")

foreach ($ambId in $ambulances) {
    Write-Host "  Resetting $ambId..." -NoNewline -ForegroundColor White
    
    try {
        # Delete all keys
        redis-cli DEL "ambulance:${ambId}:status" | Out-Null
        redis-cli DEL "ambulance:${ambId}:version" | Out-Null
        redis-cli DEL "ambulance:${ambId}:activeEmergencyId" | Out-Null
        redis-cli DEL "ambulance:${ambId}:lastUpdated" | Out-Null
        redis-cli DEL "lock:ambulance:${ambId}" | Out-Null
        
        # Set to AVAILABLE
        redis-cli SET "ambulance:${ambId}:status" "AVAILABLE" | Out-Null
        redis-cli SET "ambulance:${ambId}:version" "0" | Out-Null
        redis-cli SET "ambulance:${ambId}:lastUpdated" ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) | Out-Null
        
        Write-Host " ✓" -ForegroundColor Green
        
    } catch {
        Write-Host " ✗" -ForegroundColor Red
    }
}

Write-Host "`n✓ Redis state cleared and reset!" -ForegroundColor Green

Write-Host "`n[STEP 2/4] NOW RESTART AMBULANCE-SERVICE" -ForegroundColor Red
Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Yellow
Write-Host "║  IMPORTANT: Do this NOW (within 30 seconds)          ║" -ForegroundColor Yellow
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Yellow

Write-Host "`n  1. Open Spring Tool Suite (STS)" -ForegroundColor White
Write-Host "  2. Find Console view → ambulance-service tab" -ForegroundColor White
Write-Host "  3. Click STOP button (■)" -ForegroundColor White
Write-Host "  4. Wait 3 seconds" -ForegroundColor White
Write-Host "  5. Right-click ambulance-service project" -ForegroundColor White
Write-Host "  6. Run As → Spring Boot App" -ForegroundColor White
Write-Host "  7. Wait for 'Started AmbulanceServiceApplication'" -ForegroundColor White

Write-Host "`nHave you restarted ambulance-service? (y/n): " -NoNewline -ForegroundColor Yellow
$restarted = Read-Host

if ($restarted -ne "y") {
    Write-Host "`n⚠ Please restart ambulance-service and run this script again!" -ForegroundColor Red
    exit 1
}

Write-Host "`n[STEP 3/4] Waiting for service to start..." -ForegroundColor Yellow

Write-Host "  Waiting 10 seconds for startup..." -NoNewline -ForegroundColor Gray
Start-Sleep -Seconds 10
Write-Host " ✓" -ForegroundColor Green

Write-Host "`n[STEP 4/4] Verifying System..." -ForegroundColor Yellow

# Check service health
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method Get -TimeoutSec 5
    if ($health.status -eq "UP") {
        Write-Host "  ✓ Ambulance service is UP" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Ambulance service health: $($health.status)" -ForegroundColor Red
        Write-Host "  Wait a bit longer and try again" -ForegroundColor Yellow
        exit 1
    }
} catch {
    Write-Host "  ✗ Cannot reach ambulance service" -ForegroundColor Red
    Write-Host "  Make sure it's running in STS!" -ForegroundColor Yellow
    exit 1
}

# Check Redis state
$availableCount = 0
foreach ($ambId in $ambulances) {
    $status = redis-cli GET "ambulance:${ambId}:status"
    if ($status -eq "AVAILABLE") {
        $availableCount++
    }
    $color = if ($status -eq "AVAILABLE") { "Green" } else { "Red" }
    Write-Host "  ${ambId}: $status" -ForegroundColor $color
}

Write-Host "`n  Available: $availableCount / 3" -ForegroundColor Cyan

if ($availableCount -eq 3) {
    Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Green
    Write-Host "║              🎉 SUCCESS! ALL READY!                   ║" -ForegroundColor Green
    Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Green
    
    Write-Host "`nAll ambulances are AVAILABLE!" -ForegroundColor Green
    Write-Host "`nRun the demo now:" -ForegroundColor Yellow
    Write-Host "  ./FINAL-WORKING-DEMO.ps1" -ForegroundColor Cyan
    Write-Host ""
    
} elseif ($availableCount -eq 0) {
    Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Red
    Write-Host "║         ⚠ AMBULANCE SERVICE RESTORED OLD STATE        ║" -ForegroundColor Red
    Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Red
    
    Write-Host "`nThe service is still restoring old mission state!" -ForegroundColor Red
    Write-Host "`nThis means there's persistent state somewhere." -ForegroundColor Yellow
    Write-Host "`nOptions:" -ForegroundColor Yellow
    Write-Host "  1. Wait 60 seconds for auto-heal to run" -ForegroundColor White
    Write-Host "  2. Use nuclear option: ./FINAL-COMPLETE-RESET.ps1" -ForegroundColor White
    Write-Host "  3. Stop ambulance-service, run this script, start service" -ForegroundColor White
    Write-Host ""
    
} else {
    Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Yellow
    Write-Host "║              ⚠ PARTIAL SUCCESS                        ║" -ForegroundColor Yellow
    Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
    
    Write-Host "`n$availableCount ambulances are available." -ForegroundColor Yellow
    Write-Host "`nYou can run the demo, but only $availableCount emergencies will be assigned." -ForegroundColor Yellow
    Write-Host "`nOr wait 60 seconds for auto-heal to free the rest." -ForegroundColor Gray
    Write-Host ""
}

