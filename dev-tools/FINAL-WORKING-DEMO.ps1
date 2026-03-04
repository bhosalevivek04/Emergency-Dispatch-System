# Final Working Demo - Complete System Test

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   EMERGENCY DISPATCH SYSTEM - COMPLETE DEMO          ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan

# Step 1: Verify all services are running
Write-Host "`n[1/5] Checking Services..." -ForegroundColor Yellow

$services = @(
    @{ name = "emergency-service"; port = 8081 }
    @{ name = "ambulance-service"; port = 8082 }
    @{ name = "dispatch-service"; port = 8083 }
    @{ name = "tracking-service"; port = 8085 }
)

$allRunning = $true
foreach ($svc in $services) {
    try {
        $null = Test-NetConnection -ComputerName localhost -Port $svc.port -InformationLevel Quiet -WarningAction SilentlyContinue
        Write-Host "  ✓ $($svc.name) (port $($svc.port))" -ForegroundColor Green
    } catch {
        Write-Host "  ✗ $($svc.name) (port $($svc.port)) - NOT RUNNING" -ForegroundColor Red
        $allRunning = $false
    }
}

if (-not $allRunning) {
    Write-Host "`n⚠ Some services are not running. Please start them in STS." -ForegroundColor Red
    exit 1
}

# Step 2: Send ambulance locations
Write-Host "`n[2/5] Broadcasting Ambulance Locations..." -ForegroundColor Yellow

$ambulances = @(
    @{ id = "AMB-101"; lat = 18.5204; lon = 73.8567 }
    @{ id = "AMB-102"; lat = 18.5314; lon = 73.8446 }
    @{ id = "AMB-103"; lat = 18.5074; lon = 73.8077 }
)

foreach ($amb in $ambulances) {
    $body = @{
        ambulanceId = $amb.id
        latitude = $amb.lat
        longitude = $amb.lon
        timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    } | ConvertTo-Json

    try {
        Invoke-RestMethod -Uri "http://localhost:8085/tracking/location" `
            -Method Post `
            -Headers @{ 
                "Content-Type" = "application/json"
                "X-User-Username" = "driver1"
                "X-User-Roles" = "AMBULANCE_DRIVER"
            } `
            -Body $body | Out-Null
        Write-Host "  ✓ $($amb.id) location sent" -ForegroundColor Green
    } catch {
        Write-Host "  ✗ $($amb.id) failed" -ForegroundColor Red
    }
}

Start-Sleep -Seconds 2

# Step 3: Check ambulance availability
Write-Host "`n[3/5] Checking Ambulance Availability..." -ForegroundColor Yellow

try {
    $debug = Invoke-RestMethod -Uri "http://localhost:8083/debug/ambulance-status" -Method Get
    $available = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.ambulances.available.count" -Method Get
    
    Write-Host "  AMB-101: $($debug.redisStatus.'AMB-101')" -ForegroundColor $(if ($debug.dispatchView.'AMB-101') { "Green" } else { "Red" })
    Write-Host "  AMB-102: $($debug.redisStatus.'AMB-102')" -ForegroundColor $(if ($debug.dispatchView.'AMB-102') { "Green" } else { "Red" })
    Write-Host "  AMB-103: $($debug.redisStatus.'AMB-103')" -ForegroundColor $(if ($debug.dispatchView.'AMB-103') { "Green" } else { "Red" })
    Write-Host "`n  Total Available: $($available.measurements[0].value)" -ForegroundColor Cyan
    
    if ($available.measurements[0].value -eq 0) {
        Write-Host "`n  ⚠ No ambulances available!" -ForegroundColor Red
        Write-Host "  This means they're on active missions from previous tests." -ForegroundColor Yellow
        Write-Host "  Restart ambulance-service in STS to clear missions." -ForegroundColor Yellow
        Write-Host "`n  Continue anyway? (y/n)" -ForegroundColor Yellow
        $response = Read-Host
        if ($response -ne "y") {
            exit 1
        }
    }
} catch {
    Write-Host "  ✗ Cannot check availability" -ForegroundColor Red
}

# Step 4: Create emergencies
Write-Host "`n[4/5] Creating Emergencies..." -ForegroundColor Yellow

$locations = @(
    @{ name = "Koregaon Park"; lat = 18.5204; lon = 73.8567; priority = "HIGH" }
    @{ name = "Shivajinagar"; lat = 18.5314; lon = 73.8446; priority = "HIGH" }
    @{ name = "Kothrud"; lat = 18.5074; lon = 73.8077; priority = "HIGH" }
    @{ name = "Deccan"; lat = 18.5167; lon = 73.8422; priority = "MEDIUM" }
    @{ name = "Viman Nagar"; lat = 18.5679; lon = 73.9143; priority = "MEDIUM" }
)

$createdEmergencies = @()

foreach ($location in $locations) {
    $emergencyId = "EMG-DEMO-$(Get-Date -Format 'HHmmss')-$([guid]::NewGuid().ToString().Substring(0,4))"
    
    $emergency = @{
        emergencyId = $emergencyId
        lat = $location.lat
        lon = $location.lon
        priority = $location.priority
    } | ConvertTo-Json

    try {
        $result = Invoke-RestMethod -Uri "http://localhost:8081/emergency" `
            -Method Post `
            -Headers @{ 
                "Content-Type" = "application/json"
                "X-User-Username" = "dispatcher1"
                "X-User-Roles" = "DISPATCHER"
            } `
            -Body $emergency
        
        Write-Host "  ✓ $($location.name) - $($location.priority) - $emergencyId" -ForegroundColor Green
        $createdEmergencies += @{ id = $emergencyId; name = $location.name; priority = $location.priority }
        
        Start-Sleep -Milliseconds 300
    } catch {
        Write-Host "  ✗ Failed: $($location.name)" -ForegroundColor Red
    }
}

# Step 5: Wait and check assignments
Write-Host "`n[5/5] Waiting for Automatic Dispatch..." -ForegroundColor Yellow
Write-Host "  (Dispatch engine processes every 1 second)" -ForegroundColor Gray

for ($i = 10; $i -gt 0; $i--) {
    Write-Host "  $i..." -NoNewline -ForegroundColor Gray
    Start-Sleep -Seconds 1
}
Write-Host ""

# Check results
Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                  DISPATCH RESULTS                     ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan

$assigned = 0
$pending = 0

foreach ($emg in $createdEmergencies) {
    try {
        $status = Invoke-RestMethod -Uri "http://localhost:8081/emergency/$($emg.id)" `
            -Method Get `
            -Headers @{ 
                "X-User-Username" = "dispatcher1"
                "X-User-Roles" = "DISPATCHER"
            }
        
        if ($status.status -eq "ASSIGNED") {
            Write-Host "  ✓ $($emg.name) ($($emg.priority)) → $($status.assignedAmbulanceId)" -ForegroundColor Green
            $assigned++
        } else {
            Write-Host "  ⏳ $($emg.name) ($($emg.priority)) → PENDING" -ForegroundColor Yellow
            $pending++
        }
    } catch {
        Write-Host "  ✗ $($emg.name) - Error checking status" -ForegroundColor Red
    }
}

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                      SUMMARY                          ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan

Write-Host "`n  Total Emergencies Created: $($createdEmergencies.Count)" -ForegroundColor White
Write-Host "  ✓ Assigned: $assigned" -ForegroundColor Green
Write-Host "  ⏳ Pending: $pending" -ForegroundColor Yellow

if ($assigned -gt 0) {
    Write-Host "`n  🎉 SUCCESS! Automatic dispatch is working!" -ForegroundColor Green
    Write-Host "  $assigned emergencies were automatically assigned to ambulances!" -ForegroundColor Green
} else {
    Write-Host "`n  ⚠ No assignments made" -ForegroundColor Yellow
    Write-Host "  This likely means no ambulances are available." -ForegroundColor Yellow
    Write-Host "  Restart ambulance-service to free up ambulances." -ForegroundColor Yellow
}

# Show metrics
Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                  SYSTEM METRICS                       ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan

try {
    $known = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.ambulances.known.count" -Method Get
    $available = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.ambulances.available.count" -Method Get
    $totalQueued = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.emergencies.queued.total" -Method Get
    $totalAssigned = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.assignments.published.total" -Method Get
    
    Write-Host "`n  Known Ambulances: $($known.measurements[0].value)" -ForegroundColor White
    Write-Host "  Available Now: $($available.measurements[0].value)" -ForegroundColor White
    Write-Host "  Total Emergencies Queued: $($totalQueued.measurements[0].value)" -ForegroundColor White
    Write-Host "  Total Assignments Made: $($totalAssigned.measurements[0].value)" -ForegroundColor White
} catch {
    Write-Host "`n  Cannot access metrics" -ForegroundColor Gray
}

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                   DEMO COMPLETE                       ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan
