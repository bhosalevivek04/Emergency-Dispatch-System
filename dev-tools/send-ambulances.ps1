# Send Ambulance Locations Script
# Sends ambulance location updates to make them available for dispatch

Write-Host "=== Sending Ambulance Locations ===" -ForegroundColor Cyan
Write-Host ""

$trackingUrl = "http://localhost:8085/tracking/location"

# Send 3 ambulance locations near Pune
$ambulances = @(
    @{
        ambulanceId = "AMB-101"
        latitude = 18.5204
        longitude = 73.8567
        speed = 0.0
        heading = 0.0
    },
    @{
        ambulanceId = "AMB-102"
        latitude = 18.5314
        longitude = 73.8446
        speed = 0.0
        heading = 0.0
    },
    @{
        ambulanceId = "AMB-103"
        latitude = 18.5074
        longitude = 73.8077
        speed = 0.0
        heading = 0.0
    }
)

$headers = @{
    "X-User-Roles" = "AMBULANCE_DRIVER"
    "Content-Type" = "application/json"
}

Write-Host "Sending ambulance locations..." -ForegroundColor Yellow
Write-Host ""

foreach ($ambulance in $ambulances) {
    $body = $ambulance | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri $trackingUrl -Method POST -Body $body -Headers $headers
        
        Write-Host "✓ Ambulance Location Sent:" -ForegroundColor Green
        Write-Host "  ID: $($ambulance.ambulanceId)" -ForegroundColor White
        Write-Host "  Location: ($($ambulance.latitude), $($ambulance.longitude))" -ForegroundColor White
        Write-Host ""
    }
    catch {
        Write-Host "✗ Failed to send location for $($ambulance.ambulanceId): $_" -ForegroundColor Red
        Write-Host ""
    }
    
    Start-Sleep -Milliseconds 500
}

Write-Host "=== Ambulances are now available for dispatch ===" -ForegroundColor Green
Write-Host ""
Write-Host "Watch the dispatch-service logs to see automatic assignment!" -ForegroundColor Yellow
Write-Host "Open React frontend to see ambulances on map: http://localhost:3002" -ForegroundColor Cyan
Write-Host ""
