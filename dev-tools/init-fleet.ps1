# Initialize Ambulance Fleet
# Registers ambulances in the ambulance-service database

Write-Host "=== Initializing Ambulance Fleet ===" -ForegroundColor Cyan
Write-Host ""

$initUrl = "http://localhost:8082/diagnostic/init-fleet"

$headers = @{
    "X-User-Username" = "admin"
    "X-User-Roles" = "ADMIN"
}

Write-Host "Calling init-fleet endpoint..." -ForegroundColor Yellow

try {
    $response = Invoke-RestMethod -Uri $initUrl -Method POST -Headers $headers
    
    Write-Host "✓ Fleet Initialized Successfully!" -ForegroundColor Green
    Write-Host "  Response: $response" -ForegroundColor White
    Write-Host ""
}
catch {
    Write-Host "✗ Failed to initialize fleet: $_" -ForegroundColor Red
    Write-Host ""
    exit 1
}

# Check fleet status
Write-Host "Checking fleet status..." -ForegroundColor Yellow
$statusUrl = "http://localhost:8082/diagnostic/fleet-status"

try {
    $status = Invoke-RestMethod -Uri $statusUrl -Method GET -Headers $headers
    
    Write-Host "✓ Fleet Status:" -ForegroundColor Green
    Write-Host $status -ForegroundColor White
}
catch {
    Write-Host "✗ Failed to get fleet status: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Next Steps ===" -ForegroundColor Cyan
Write-Host "1. Send ambulance locations: .\send-ambulances.ps1" -ForegroundColor White
Write-Host "2. Watch dispatch-service logs for automatic assignment" -ForegroundColor White
Write-Host "3. Open React frontend: http://localhost:3002" -ForegroundColor White
Write-Host ""
