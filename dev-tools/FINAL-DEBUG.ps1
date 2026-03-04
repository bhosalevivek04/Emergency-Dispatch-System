# Final Debug Script - Complete Diagnosis

Write-Host "`n=== FINAL COMPREHENSIVE DEBUG ===" -ForegroundColor Cyan

Write-Host "`n" -ForegroundColor Yellow
Write-Host "INSTRUCTIONS:" -ForegroundColor Green
Write-Host "1. Make sure dispatch-service is restarted in STS" -ForegroundColor White
Write-Host "2. This script will test Redis connectivity and status checking" -ForegroundColor White
Write-Host "3. Then create an emergency and show detailed logs" -ForegroundColor White

$response = Read-Host "`nHave you restarted dispatch-service? (y/n)"
if ($response -ne "y") {
    Write-Host "`nPlease restart dispatch-service in STS first!" -ForegroundColor Red
    Write-Host "Right-click dispatch-service -> Run As -> Spring Boot App" -ForegroundColor Yellow
    exit 1
}

# Test 1: Check Redis directly
Write-Host "`n=== TEST 1: Redis Direct Check ===" -ForegroundColor Cyan
$statuses = docker exec redis redis-cli MGET "ambulance:AMB-101:status" "ambulance:AMB-102:status" "ambulance:AMB-103:status"
$statusArray = $statuses -split "`n"
Write-Host "AMB-101: $($statusArray[0])" -ForegroundColor White
Write-Host "AMB-102: $($statusArray[1])" -ForegroundColor White
Write-Host "AMB-103: $($statusArray[2])" -ForegroundColor White

# Test 2: Check dispatch debug endpoint
Write-Host "`n=== TEST 2: Dispatch Service Redis Check ===" -ForegroundColor Cyan
try {
    $debug = Invoke-RestMethod -Uri "http://localhost:8083/debug/ambulance-status" -Method Get
    Write-Host "Redis Status (as seen by dispatch):" -ForegroundColor Yellow
    Write-Host "  AMB-101: $($debug.redisStatus.'AMB-101')" -ForegroundColor White
    Write-Host "  AMB-102: $($debug.redisStatus.'AMB-102')" -ForegroundColor White
    Write-Host "  AMB-103: $($debug.redisStatus.'AMB-103')" -ForegroundColor White
    
    Write-Host "`nDispatch Availability Check:" -ForegroundColor Yellow
    Write-Host "  AMB-101: $($debug.dispatchView.'AMB-101')" -ForegroundColor $(if ($debug.dispatchView.'AMB-101') { "Green" } else { "Red" })
    Write-Host "  AMB-102: $($debug.dispatchView.'AMB-102')" -ForegroundColor $(if ($debug.dispatchView.'AMB-102') { "Green" } else { "Red" })
    Write-Host "  AMB-103: $($debug.dispatchView.'AMB-103')" -ForegroundColor $(if ($debug.dispatchView.'AMB-103') { "Green" } else { "Red" })
    
    $allAvailable = $debug.dispatchView.'AMB-101' -and $debug.dispatchView.'AMB-102' -and $debug.dispatchView.'AMB-103'
    if ($allAvailable) {
        Write-Host "`n✓ All ambulances are seen as AVAILABLE by dispatch!" -ForegroundColor Green
    } else {
        Write-Host "`n✗ Dispatch sees some ambulances as NOT AVAILABLE!" -ForegroundColor Red
        Write-Host "This is the bug! Redis shows AVAILABLE but dispatch sees otherwise." -ForegroundColor Yellow
    }
} catch {
    Write-Host "Cannot access debug endpoint: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Make sure dispatch-service is restarted with the new DebugController" -ForegroundColor Yellow
    exit 1
}

# Test 3: Check dispatch metrics
Write-Host "`n=== TEST 3: Dispatch Metrics ===" -ForegroundColor Cyan
try {
    $known = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.ambulances.known.count" -Method Get
    $available = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.ambulances.available.count" -Method Get
    Write-Host "Known ambulances: $($known.measurements[0].value)" -ForegroundColor White
    Write-Host "Available ambulances: $($available.measurements[0].value)" -ForegroundColor White
    
    if ($available.measurements[0].value -eq 0) {
        Write-Host "`n✗ Metrics show 0 available even though Redis has AVAILABLE!" -ForegroundColor Red
    } else {
        Write-Host "`n✓ Metrics show ambulances are available!" -ForegroundColor Green
    }
} catch {
    Write-Host "Cannot access metrics" -ForegroundColor Red
}

# Test 4: Create emergency and watch
Write-Host "`n=== TEST 4: Create Emergency ===" -ForegroundColor Cyan
$emergency = @{
    latitude = 18.5196
    longitude = 73.8553
    priority = "HIGH"
    description = "Final debug test"
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
    
    Write-Host "Emergency created: $($result.emergencyId)" -ForegroundColor Green
    $emergencyId = $result.emergencyId
} catch {
    Write-Host "Failed to create emergency: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host "`nWaiting 5 seconds for dispatch..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# Check result
try {
    $status = Invoke-RestMethod -Uri "http://localhost:8081/emergency/$emergencyId" `
        -Method Get `
        -Headers @{ 
            "X-User-Username" = "dispatcher1"
            "X-User-Roles" = "DISPATCHER"
        }
    
    Write-Host "`nEmergency Status: $($status.status)" -ForegroundColor $(if ($status.status -eq "ASSIGNED") { "Green" } else { "Red" })
    if ($status.assignedAmbulanceId) {
        Write-Host "Assigned to: $($status.assignedAmbulanceId)" -ForegroundColor Green
        Write-Host "`n✓✓✓ SUCCESS! Dispatch is working!" -ForegroundColor Green
    } else {
        Write-Host "Not assigned yet" -ForegroundColor Red
        Write-Host "`n✗ Dispatch failed to assign ambulance" -ForegroundColor Red
    }
} catch {
    Write-Host "Failed to check status" -ForegroundColor Red
}

Write-Host "`n=== NEXT STEPS ===" -ForegroundColor Cyan
Write-Host "1. Check dispatch-service console in STS for DEBUG logs" -ForegroundColor Yellow
Write-Host "2. Look for lines containing:" -ForegroundColor Yellow
Write-Host "   - 'Finding nearest ambulance'" -ForegroundColor White
Write-Host "   - 'Checking ambulance ambulanceId=AMB-XXX available=?'" -ForegroundColor White
Write-Host "   - 'Checked ambulance availability ambulanceId=AMB-XXX rawStatus=? status=? available=?'" -ForegroundColor White
Write-Host "3. Share those log lines to identify the exact issue" -ForegroundColor Yellow
