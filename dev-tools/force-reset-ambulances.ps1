# Force Reset All Ambulances - Clear Redis State

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║        FORCE RESET ALL AMBULANCES                     ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan

Write-Host "`nThis will forcefully reset all ambulances to AVAILABLE..." -ForegroundColor Yellow

Write-Host "`n[1/3] Clearing Redis State..." -ForegroundColor Yellow

$ambulances = @("AMB-101", "AMB-102", "AMB-103")

foreach ($ambId in $ambulances) {
    Write-Host "`n  Resetting $ambId..." -ForegroundColor White
    
    try {
        # Delete all keys for this ambulance
        redis-cli DEL "ambulance:${ambId}:status" | Out-Null
        redis-cli DEL "ambulance:${ambId}:version" | Out-Null
        redis-cli DEL "ambulance:${ambId}:activeEmergencyId" | Out-Null
        redis-cli DEL "ambulance:${ambId}:lastUpdated" | Out-Null
        redis-cli DEL "lock:ambulance:${ambId}" | Out-Null
        
        # Set to AVAILABLE with version 0
        redis-cli SET "ambulance:${ambId}:status" "AVAILABLE" | Out-Null
        redis-cli SET "ambulance:${ambId}:version" "0" | Out-Null
        redis-cli SET "ambulance:${ambId}:lastUpdated" ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) | Out-Null
        
        Write-Host "    ✓ Cleared and reset to AVAILABLE" -ForegroundColor Green
        
    } catch {
        Write-Host "    ✗ Failed to reset" -ForegroundColor Red
    }
}

Write-Host "`n[2/3] Verifying Reset..." -ForegroundColor Yellow

Start-Sleep -Seconds 2

foreach ($ambId in $ambulances) {
    $status = redis-cli GET "ambulance:${ambId}:status"
    $activeEmergency = redis-cli GET "ambulance:${ambId}:activeEmergencyId"
    
    Write-Host "`n  $ambId:" -ForegroundColor White
    Write-Host "    Status: $status" -ForegroundColor $(if ($status -eq "AVAILABLE") { "Green" } else { "Red" })
    Write-Host "    Active Emergency: $(if ($activeEmergency) { $activeEmergency } else { '(none)' })" -ForegroundColor Gray
}

Write-Host "`n[3/3] Checking Dispatch View..." -ForegroundColor Yellow

Start-Sleep -Seconds 2

try {
    $metrics = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.ambulances.available.count" -Method Get
    Write-Host "`n  Available count: $($metrics.measurements[0].value)" -ForegroundColor Cyan
    
    if ($metrics.measurements[0].value -eq 3) {
        Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Green
        Write-Host "║              ✓ ALL AMBULANCES AVAILABLE!              ║" -ForegroundColor Green
        Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Green
        
        Write-Host "`n🎉 Success! All ambulances are now available!" -ForegroundColor Green
        Write-Host "`nRun the demo now:" -ForegroundColor Yellow
        Write-Host "  ./FINAL-WORKING-DEMO.ps1" -ForegroundColor Cyan
        
    } else {
        Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Yellow
        Write-Host "║           ⚠ AMBULANCE SERVICE IS RESTORING STATE      ║" -ForegroundColor Yellow
        Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
        
        Write-Host "`nThe ambulance-service is still restoring old state from memory!" -ForegroundColor Red
        Write-Host "`nYou MUST restart ambulance-service to clear in-memory state:" -ForegroundColor Yellow
        Write-Host "  1. Stop ambulance-service in STS Console" -ForegroundColor White
        Write-Host "  2. Wait 5 seconds" -ForegroundColor White
        Write-Host "  3. Start ambulance-service" -ForegroundColor White
        Write-Host "  4. Run this script again" -ForegroundColor White
        Write-Host "`nOR use nuclear option:" -ForegroundColor Yellow
        Write-Host "  ./FINAL-COMPLETE-RESET.ps1" -ForegroundColor Cyan
    }
    
} catch {
    Write-Host "  ✗ Cannot check dispatch service" -ForegroundColor Red
}

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                    RESET COMPLETE                     ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

