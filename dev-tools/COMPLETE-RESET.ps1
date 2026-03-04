# Complete System Reset
# Cleans all state and prepares for a fresh demo

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Red
Write-Host "║   COMPLETE SYSTEM RESET - Clean Slate                     ║" -ForegroundColor Red
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Red
Write-Host ""
Write-Host "⚠ This will reset ALL system state!" -ForegroundColor Yellow
Write-Host ""

$confirm = Read-Host "Continue? (yes/no)"
if ($confirm -ne "yes") {
    Write-Host "Cancelled." -ForegroundColor Gray
    exit
}

Write-Host ""

# 1. Reset Kafka consumer offsets
Write-Host "1. Resetting Kafka consumer offsets..." -ForegroundColor Yellow
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group emergency-status-group --reset-offsets --to-latest --all-topics --execute 2>$null | Out-Null
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group dispatch-group --reset-offsets --to-latest --all-topics --execute 2>$null | Out-Null
Write-Host "   ✓ Kafka offsets reset" -ForegroundColor Green

# 2. Clear Redis
Write-Host "2. Clearing Redis..." -ForegroundColor Yellow
docker exec redis redis-cli FLUSHALL | Out-Null
Write-Host "   ✓ Redis cleared" -ForegroundColor Green

# 3. Set ambulances to AVAILABLE
Write-Host "3. Setting ambulances to AVAILABLE..." -ForegroundColor Yellow
docker exec redis redis-cli SET "ambulance:AMB-101:status" "AVAILABLE" | Out-Null
docker exec redis redis-cli SET "ambulance:AMB-102:status" "AVAILABLE" | Out-Null
docker exec redis redis-cli SET "ambulance:AMB-103:status" "AVAILABLE" | Out-Null
docker exec redis redis-cli SET "ambulance:AMB-101:version" "0" | Out-Null
docker exec redis redis-cli SET "ambulance:AMB-102:version" "0" | Out-Null
docker exec redis redis-cli SET "ambulance:AMB-103:version" "0" | Out-Null
Write-Host "   ✓ Ambulances set to AVAILABLE" -ForegroundColor Green

# 4. Clear database emergencies (optional - keep for history)
Write-Host "4. Database cleanup (keeping history)..." -ForegroundColor Yellow
Write-Host "   ℹ Keeping existing emergencies for reference" -ForegroundColor Gray

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║   Reset Complete!                                          ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Restart ALL services in STS (emergency, dispatch, ambulance, tracking, auth, gateway)" -ForegroundColor White
Write-Host "2. Wait for all services to start cleanly" -ForegroundColor White
Write-Host "3. Run: .\full-demo.ps1" -ForegroundColor White
Write-Host ""
Write-Host "Services to verify are running:" -ForegroundColor Yellow
Write-Host "  • emergency-service (8081)" -ForegroundColor Gray
Write-Host "  • ambulance-service (8082)" -ForegroundColor Gray
Write-Host "  • dispatch-service (8083)" -ForegroundColor Gray
Write-Host "  • tracking-service (8085)" -ForegroundColor Gray
Write-Host "  • auth-service (8086)" -ForegroundColor Gray
Write-Host "  • api-gateway (8080)" -ForegroundColor Gray
Write-Host ""
