# Create Test Emergency - Working Version

Write-Host "`n=== CREATING TEST EMERGENCY ===" -ForegroundColor Cyan

$emergencyId = "EMG-TEST-$(Get-Date -Format 'HHmmss')"

$emergency = @{
    emergencyId = $emergencyId
    lat = 18.5196
    lon = 73.8553
    priority = "HIGH"
} | ConvertTo-Json

Write-Host "`nCreating emergency..." -ForegroundColor Yellow
Write-Host "  ID: $emergencyId" -ForegroundColor Gray
Write-Host "  Location: (18.5196, 73.8553)" -ForegroundColor Gray
Write-Host "  Priority: HIGH" -ForegroundColor Gray

try {
    $result = Invoke-RestMethod -Uri "http://localhost:8081/emergency" `
        -Method Post `
        -Headers @{ 
            "Content-Type" = "application/json"
            "X-User-Username" = "dispatcher1"
            "X-User-Roles" = "DISPATCHER"
        } `
        -Body $emergency
    
    Write-Host "`n✓ Emergency created successfully!" -ForegroundColor Green
    Write-Host "  Emergency ID: $($result.emergencyId)" -ForegroundColor White
    Write-Host "  Initial Status: $($result.status)" -ForegroundColor White
    
    # Wait for automatic dispatch
    Write-Host "`nWaiting 5 seconds for automatic dispatch..." -ForegroundColor Yellow
    Start-Sleep -Seconds 5
    
    # Check final status
    $status = Invoke-RestMethod -Uri "http://localhost:8081/emergency/$emergencyId" `
        -Method Get `
        -Headers @{ 
            "X-User-Username" = "dispatcher1"
            "X-User-Roles" = "DISPATCHER"
        }
    
    Write-Host "`n=== FINAL STATUS ===" -ForegroundColor Cyan
    Write-Host "  Status: $($status.status)" -ForegroundColor $(if ($status.status -eq "ASSIGNED") { "Green" } else { "Yellow" })
    
    if ($status.assignedAmbulanceId) {
        Write-Host "  Assigned Ambulance: $($status.assignedAmbulanceId)" -ForegroundColor Green
        Write-Host "`n✓✓✓ SUCCESS! Emergency was automatically assigned!" -ForegroundColor Green
    } else {
        Write-Host "  Not assigned yet (may need more time or no ambulances available)" -ForegroundColor Yellow
    }
    
} catch {
    Write-Host "`n✗ Failed to create emergency" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
}
