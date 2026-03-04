# Refresh Ambulance Locations in Dispatch Cache

Write-Host "`n=== REFRESHING AMBULANCE LOCATIONS ===" -ForegroundColor Cyan

Write-Host "`nSending fresh location updates to tracking-service..." -ForegroundColor Yellow
Write-Host "(This will broadcast to Kafka and update dispatch cache)" -ForegroundColor Gray

$ambulances = @(
    @{ id = "AMB-101"; lat = 18.5204; lon = 73.8567; name = "Koregaon Park" }
    @{ id = "AMB-102"; lat = 18.5314; lon = 73.8446; name = "Shivajinagar" }
    @{ id = "AMB-103"; lat = 18.5074; lon = 73.8077; name = "Kothrud" }
)

$token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJkcml2ZXIxIiwicm9sZXMiOiJBTUJVTEFOQ0VfRFJJVkVSIiwiaWF0IjoxNzA5NTQ0MDAwLCJleHAiOjI3MDk1NDc2MDB9.placeholder"

foreach ($amb in $ambulances) {
    $body = @{
        ambulanceId = $amb.id
        latitude = $amb.lat
        longitude = $amb.lon
        timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8085/tracking/location" `
            -Method Post `
            -Headers @{ 
                "Content-Type" = "application/json"
                "Authorization" = "Bearer $token"
                "X-User-Username" = "driver1"
                "X-User-Roles" = "AMBULANCE_DRIVER"
            } `
            -Body $body
        
        Write-Host "  ✓ $($amb.id) location sent" -ForegroundColor Green
    } catch {
        Write-Host "  ✗ Failed to send $($amb.id): $($_.Exception.Message)" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 500
}

Write-Host "`nWaiting 3 seconds for dispatch to consume updates..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

Write-Host "`nChecking dispatch state after refresh..." -ForegroundColor Yellow
try {
    $debug = Invoke-RestMethod -Uri "http://localhost:8083/debug/ambulance-status" -Method Get
    Write-Host "`nDispatch now sees:" -ForegroundColor Cyan
    Write-Host "  AMB-101: $($debug.redisStatus.'AMB-101') - Available: $($debug.dispatchView.'AMB-101')" -ForegroundColor $(if ($debug.dispatchView.'AMB-101') { "Green" } else { "Red" })
    Write-Host "  AMB-102: $($debug.redisStatus.'AMB-102') - Available: $($debug.dispatchView.'AMB-102')" -ForegroundColor $(if ($debug.dispatchView.'AMB-102') { "Green" } else { "Red" })
    Write-Host "  AMB-103: $($debug.redisStatus.'AMB-103') - Available: $($debug.dispatchView.'AMB-103')" -ForegroundColor $(if ($debug.dispatchView.'AMB-103') { "Green" } else { "Red" })
    
    $allAvailable = $debug.dispatchView.'AMB-101' -and $debug.dispatchView.'AMB-102' -and $debug.dispatchView.'AMB-103'
    if ($allAvailable) {
        Write-Host "`n✓✓✓ All ambulances now available!" -ForegroundColor Green
    } else {
        Write-Host "`n✗ Some ambulances still not available" -ForegroundColor Red
        Write-Host "This means Redis status is being updated by ambulance-service FSM" -ForegroundColor Yellow
        Write-Host "Check if there are active emergencies assigned to these ambulances" -ForegroundColor Yellow
    }
} catch {
    Write-Host "Cannot access debug endpoint" -ForegroundColor Red
}

Write-Host "`n=== NEXT: Create Test Emergency ===" -ForegroundColor Cyan
Write-Host "Run this command to create an emergency:" -ForegroundColor Yellow
Write-Host '  Invoke-RestMethod -Uri "http://localhost:8081/emergency" -Method Post -Headers @{"Content-Type"="application/json";"X-User-Username"="dispatcher1";"X-User-Roles"="DISPATCHER"} -Body ''{"latitude":18.5196,"longitude":73.8553,"priority":"HIGH","description":"Test emergency"}''' -ForegroundColor White
