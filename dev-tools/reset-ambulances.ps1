# Reset Ambulance Status to AVAILABLE
# Clears Redis status keys so ambulances become available for dispatch

Write-Host "=== Resetting Ambulance Status ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "Clearing ambulance status in Redis..." -ForegroundColor Yellow

# Clear Redis keys for ambulance status
$ambulances = @("AMB-101", "AMB-102", "AMB-103")

foreach ($ambulanceId in $ambulances) {
    try {
        # Delete the status key from Redis
        docker exec -it redis redis-cli DEL "ambulance:$ambulanceId:status" | Out-Null
        Write-Host "  ✓ Cleared status for $ambulanceId" -ForegroundColor Green
    }
    catch {
        Write-Host "  ✗ Failed to clear $ambulanceId : $_" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "✓ Ambulance statuses reset to AVAILABLE" -ForegroundColor Green
Write-Host ""
Write-Host "Now the dispatch service will see them as available!" -ForegroundColor Cyan
Write-Host "Watch the dispatch-service logs for automatic assignment." -ForegroundColor Yellow
Write-Host ""
