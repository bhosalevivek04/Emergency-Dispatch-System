# Final Complete Reset - Clears Everything

Write-Host "`n=== FINAL COMPLETE RESET ===" -ForegroundColor Cyan

Write-Host "`n1. Clearing ALL Redis keys..." -ForegroundColor Yellow
docker exec redis redis-cli FLUSHALL | Out-Null
Write-Host "  ✓ Redis completely cleared" -ForegroundColor Green

Write-Host "`n2. Resetting Kafka offsets..." -ForegroundColor Yellow
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group emergency-status-group --reset-offsets --to-latest --all-topics --execute 2>$null | Out-Null
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group dispatch-group --reset-offsets --to-latest --all-topics --execute 2>$null | Out-Null
Write-Host "  ✓ Kafka offsets reset" -ForegroundColor Green

Write-Host "`n3. Setting fresh ambulance status..." -ForegroundColor Yellow
docker exec redis redis-cli MSET `
    "ambulance:AMB-101:status" "AVAILABLE" `
    "ambulance:AMB-102:status" "AVAILABLE" `
    "ambulance:AMB-103:status" "AVAILABLE" `
    "ambulance:AMB-101:version" "0" `
    "ambulance:AMB-102:version" "0" `
    "ambulance:AMB-103:version" "0" | Out-Null
Write-Host "  ✓ All ambulances set to AVAILABLE" -ForegroundColor Green

Write-Host "`n=== CRITICAL: RESTART ALL SERVICES IN STS ===" -ForegroundColor Red
Write-Host "`nYou MUST restart these services in this order:" -ForegroundColor Yellow
Write-Host "  1. emergency-service (8081)" -ForegroundColor White
Write-Host "  2. ambulance-service (8082)" -ForegroundColor White
Write-Host "  3. dispatch-service (8083)" -ForegroundColor White
Write-Host "  4. tracking-service (8085)" -ForegroundColor White

Write-Host "`nWhy? Because:" -ForegroundColor Yellow
Write-Host "  - Ambulance-service has in-memory state of active emergencies" -ForegroundColor White
Write-Host "  - It keeps updating Redis status to ASSIGNED/ON_ROUTE" -ForegroundColor White
Write-Host "  - Only a restart will clear this state" -ForegroundColor White

Write-Host "`n=== AFTER RESTART ===" -ForegroundColor Cyan
Write-Host "1. Run: ./refresh-ambulances.ps1" -ForegroundColor White
Write-Host "2. Run: ./FINAL-DEBUG.ps1" -ForegroundColor White
Write-Host "3. All ambulances should be AVAILABLE!" -ForegroundColor Green
