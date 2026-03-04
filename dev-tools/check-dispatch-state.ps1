# Check what dispatch service actually knows about ambulances

Write-Host "=== DISPATCH SERVICE STATE CHECK ===" -ForegroundColor Cyan

# Check dispatch metrics endpoint to see ambulance count
Write-Host "`n1. Checking dispatch metrics..." -ForegroundColor Yellow
try {
    $metrics = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.ambulances.known.count" -Method Get
    Write-Host "  Known ambulances in dispatch cache: $($metrics.measurements[0].value)" -ForegroundColor White
    
    $available = Invoke-RestMethod -Uri "http://localhost:8083/actuator/metrics/dispatch.ambulances.available.count" -Method Get
    Write-Host "  Available ambulances per Redis: $($available.measurements[0].value)" -ForegroundColor White
} catch {
    Write-Host "  Cannot access metrics: $($_.Exception.Message)" -ForegroundColor Red
}

# Check Redis directly
Write-Host "`n2. Redis ambulance status:" -ForegroundColor Yellow
$amb101 = docker exec redis redis-cli GET "ambulance:AMB-101:status"
$amb102 = docker exec redis redis-cli GET "ambulance:AMB-102:status"
$amb103 = docker exec redis redis-cli GET "ambulance:AMB-103:status"
Write-Host "  AMB-101: $amb101" -ForegroundColor White
Write-Host "  AMB-102: $amb102" -ForegroundColor White
Write-Host "  AMB-103: $amb103" -ForegroundColor White

# Check if Kafka has any messages
Write-Host "`n3. Kafka ambulance-location-topic message count:" -ForegroundColor Yellow
$offset = docker exec kafka kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic ambulance-location-topic --time -1 2>$null
Write-Host "  $offset" -ForegroundColor White

# Check dispatch consumer group lag
Write-Host "`n4. Dispatch consumer group status:" -ForegroundColor Yellow
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group dispatch-group --describe 2>$null

Write-Host "`n=== ANALYSIS ===" -ForegroundColor Cyan
Write-Host "If 'Known ambulances' = 0: Dispatch never received location updates from Kafka" -ForegroundColor Yellow
Write-Host "If Kafka message count = 0: Tracking service never published locations" -ForegroundColor Yellow
Write-Host "If Redis shows AVAILABLE but dispatch shows 0 available: Mismatch in status logic" -ForegroundColor Yellow
