# Show Active Ambulance Missions - Diagnostic Tool

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║        ACTIVE AMBULANCE MISSIONS DIAGNOSTIC           ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan

Write-Host "`nThis shows why ambulances are not available..." -ForegroundColor Gray

Write-Host "`n[1/2] Checking Redis State..." -ForegroundColor Yellow

$ambulances = @("AMB-101", "AMB-102", "AMB-103")

foreach ($ambId in $ambulances) {
    Write-Host "`n  $ambId:" -ForegroundColor Cyan
    
    try {
        $status = redis-cli GET "ambulance:${ambId}:status"
        $version = redis-cli GET "ambulance:${ambId}:version"
        $activeEmergency = redis-cli GET "ambulance:${ambId}:activeEmergencyId"
        $lastUpdated = redis-cli GET "ambulance:${ambId}:lastUpdated"
        
        Write-Host "    Status: $status" -ForegroundColor $(if ($status -eq "AVAILABLE") { "Green" } else { "Red" })
        Write-Host "    Version: $version" -ForegroundColor Gray
        Write-Host "    Active Emergency: $activeEmergency" -ForegroundColor $(if ($activeEmergency) { "Yellow" } else { "Gray" })
        
        if ($lastUpdated) {
            $timestamp = [long]$lastUpdated
            $date = [DateTimeOffset]::FromUnixTimeMilliseconds($timestamp).LocalDateTime
            $ageSeconds = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - $timestamp) / 1000
            Write-Host "    Last Updated: $date ($([int]$ageSeconds)s ago)" -ForegroundColor Gray
        }
        
        # Check if there's a lock
        $lock = redis-cli GET "lock:ambulance:${ambId}"
        if ($lock) {
            Write-Host "    ⚠ LOCKED (dispatch is assigning)" -ForegroundColor Yellow
        }
        
    } catch {
        Write-Host "    ✗ Cannot read from Redis" -ForegroundColor Red
    }
}

Write-Host "`n[2/2] Checking Emergency Queue..." -ForegroundColor Yellow

try {
    $highCount = redis-cli LLEN "dispatch:queue:HIGH"
    $mediumCount = redis-cli LLEN "dispatch:queue:MEDIUM"
    $lowCount = redis-cli LLEN "dispatch:queue:LOW"
    
    Write-Host "`n  Queue Depth:" -ForegroundColor White
    Write-Host "    HIGH priority: $highCount" -ForegroundColor Red
    Write-Host "    MEDIUM priority: $mediumCount" -ForegroundColor Yellow
    Write-Host "    LOW priority: $lowCount" -ForegroundColor Green
    Write-Host "    Total waiting: $([int]$highCount + [int]$mediumCount + [int]$lowCount)" -ForegroundColor Cyan
    
    if ([int]$highCount -gt 0) {
        Write-Host "`n  Sample HIGH priority emergency:" -ForegroundColor Yellow
        $sample = redis-cli LINDEX "dispatch:queue:HIGH" 0
        if ($sample) {
            $emergency = $sample | ConvertFrom-Json
            Write-Host "    Emergency ID: $($emergency.emergencyId)" -ForegroundColor White
            Write-Host "    Location: $($emergency.lat), $($emergency.lon)" -ForegroundColor Gray
            Write-Host "    Priority: $($emergency.priority)" -ForegroundColor Red
        }
    }
    
} catch {
    Write-Host "  ✗ Cannot check queue" -ForegroundColor Red
}

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                     DIAGNOSIS                         ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan

Write-Host "`nWhat's happening:" -ForegroundColor Yellow

$amb101Status = redis-cli GET "ambulance:AMB-101:status"
$amb102Status = redis-cli GET "ambulance:AMB-102:status"
$amb103Status = redis-cli GET "ambulance:AMB-103:status"

$availableCount = 0
if ($amb101Status -eq "AVAILABLE") { $availableCount++ }
if ($amb102Status -eq "AVAILABLE") { $availableCount++ }
if ($amb103Status -eq "AVAILABLE") { $availableCount++ }

if ($availableCount -eq 0) {
    Write-Host "`n  ❌ ALL ambulances are on active missions" -ForegroundColor Red
    Write-Host "  ❌ New emergencies are queued and waiting" -ForegroundColor Red
    Write-Host "`n  Why this happens:" -ForegroundColor Yellow
    Write-Host "    • Ambulance-service has in-memory mission state" -ForegroundColor White
    Write-Host "    • Even if you reset Redis, the service restores the state" -ForegroundColor White
    Write-Host "    • This is CORRECT behavior for production resilience" -ForegroundColor White
    Write-Host "`n  Solution:" -ForegroundColor Green
    Write-Host "    1. Restart ambulance-service in STS" -ForegroundColor Cyan
    Write-Host "    2. Run: ./verify-after-restart.ps1" -ForegroundColor Cyan
    Write-Host "    3. Run: ./FINAL-WORKING-DEMO.ps1" -ForegroundColor Cyan
    
} elseif ($availableCount -eq 3) {
    Write-Host "`n  ✓ All ambulances are AVAILABLE!" -ForegroundColor Green
    Write-Host "  ✓ Ready for demo!" -ForegroundColor Green
    Write-Host "`n  Run: ./FINAL-WORKING-DEMO.ps1" -ForegroundColor Cyan
    
} else {
    Write-Host "`n  ⚠ $availableCount / 3 ambulances available" -ForegroundColor Yellow
    Write-Host "  ⚠ Some ambulances still on missions" -ForegroundColor Yellow
    Write-Host "`n  Options:" -ForegroundColor Yellow
    Write-Host "    • Wait for auto-heal (runs every 60 seconds)" -ForegroundColor White
    Write-Host "    • Restart ambulance-service in STS" -ForegroundColor White
    Write-Host "    • Run: ./FINAL-COMPLETE-RESET.ps1" -ForegroundColor White
}

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                  DIAGNOSTIC COMPLETE                  ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

