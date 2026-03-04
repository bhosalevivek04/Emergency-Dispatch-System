# Simple Emergency Creation Script
# Creates emergencies directly without authentication (for testing)

Write-Host "=== Creating Test Emergencies ===" -ForegroundColor Cyan
Write-Host ""

$emergencyUrl = "http://localhost:8081/emergency"

# Create emergencies directly on emergency service (bypassing gateway for demo)
$emergencies = @(
    @{
        emergencyId = "EMG-DEMO-$(Get-Random -Maximum 9999)"
        lat = 18.5204
        lon = 73.8567
        priority = "HIGH"
    },
    @{
        emergencyId = "EMG-DEMO-$(Get-Random -Maximum 9999)"
        lat = 18.5314
        lon = 73.8446
        priority = "MEDIUM"
    },
    @{
        emergencyId = "EMG-DEMO-$(Get-Random -Maximum 9999)"
        lat = 18.5074
        lon = 73.8077
        priority = "HIGH"
    }
)

$headers = @{
    "X-User-Username" = "dispatcher_demo"
    "X-User-Roles" = "DISPATCHER"
    "Content-Type" = "application/json"
}

Write-Host "Creating emergencies directly on emergency-service..." -ForegroundColor Yellow
Write-Host ""

foreach ($emergency in $emergencies) {
    $body = $emergency | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri $emergencyUrl -Method POST -Body $body -Headers $headers
        
        Write-Host "✓ Emergency Created:" -ForegroundColor Green
        Write-Host "  ID: $($response.emergencyId)" -ForegroundColor White
        Write-Host "  Location: ($($response.lat), $($response.lon))" -ForegroundColor White
        Write-Host "  Priority: $($response.priority)" -ForegroundColor $(if ($response.priority -eq "HIGH") { "Red" } else { "Yellow" })
        Write-Host "  Status: $($response.status)" -ForegroundColor Cyan
        Write-Host "  Created At: $($response.createdAt)" -ForegroundColor Gray
        Write-Host ""
    }
    catch {
        Write-Host "✗ Failed to create emergency: $_" -ForegroundColor Red
        Write-Host ""
    }
    
    Start-Sleep -Milliseconds 500
}

# View all pending emergencies
Write-Host "Fetching pending emergencies..." -ForegroundColor Yellow
Write-Host ""

try {
    $pending = Invoke-RestMethod -Uri "$emergencyUrl/pending" -Method GET -Headers $headers
    
    Write-Host "Total Pending Emergencies: $($pending.Count)" -ForegroundColor Cyan
    Write-Host ""
    
    foreach ($emg in $pending) {
        $priorityColor = switch ($emg.priority) {
            "HIGH" { "Red" }
            "MEDIUM" { "Yellow" }
            "LOW" { "Green" }
            default { "White" }
        }
        
        Write-Host "  Emergency: $($emg.emergencyId)" -ForegroundColor White
        Write-Host "    Priority: $($emg.priority)" -ForegroundColor $priorityColor
        Write-Host "    Status: $($emg.status)" -ForegroundColor Cyan
        Write-Host "    Location: ($($emg.lat), $($emg.lon))" -ForegroundColor Gray
        Write-Host ""
    }
}
catch {
    Write-Host "✗ Failed to fetch pending emergencies: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Next Steps ===" -ForegroundColor Cyan
Write-Host "1. Open React frontend: http://localhost:3002" -ForegroundColor White
Write-Host "2. View emergencies on the map" -ForegroundColor White
Write-Host "3. Send ambulance locations to see tracking" -ForegroundColor White
Write-Host ""
Write-Host "To send ambulance location:" -ForegroundColor Yellow
Write-Host '  $body = ''{"ambulanceId":"AMB-101","latitude":18.5204,"longitude":73.8567,"speed":45.5,"heading":90.0}''' -ForegroundColor Gray
Write-Host '  Invoke-RestMethod -Uri "http://localhost:8085/tracking/location" -Method POST -Body $body -ContentType "application/json" -Headers @{"X-User-Roles"="AMBULANCE_DRIVER"}' -ForegroundColor Gray
Write-Host ""
