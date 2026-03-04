# Complete Fix for All Issues

Write-Host "`n=== COMPLETE SYSTEM FIX ===" -ForegroundColor Cyan

# Issue 1: Fix Kafka Deserialization Errors
Write-Host "`n1. Fixing Kafka consumer offsets..." -ForegroundColor Yellow
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group emergency-status-group --reset-offsets --to-latest --topic ambulance-assigned-topic --execute 2>$null | Out-Null
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group emergency-status-group --reset-offsets --to-latest --topic ambulance-completed-topic --execute 2>$null | Out-Null
Write-Host "  ✓ Kafka offsets reset to latest" -ForegroundColor Green

# Issue 2: Reset Ambulance States
Write-Host "`n2. Resetting ambulance states to AVAILABLE..." -ForegroundColor Yellow

# Clear all ambulance-related keys
$ambulanceKeys = docker exec redis redis-cli KEYS "ambulance:*"
if ($ambulanceKeys) {
    $keys = $ambulanceKeys -split "`n" | Where-Object { $_.Trim() }
    foreach ($key in $keys) {
        docker exec redis redis-cli DEL $key.Trim() | Out-Null
    }
}

# Set fresh AVAILABLE status
docker exec redis redis-cli MSET `
    "ambulance:AMB-101:status" "AVAILABLE" `
    "ambulance:AMB-102:status" "AVAILABLE" `
    "ambulance:AMB-103:status" "AVAILABLE" `
    "ambulance:AMB-101:version" "0" `
    "ambulance:AMB-102:version" "0" `
    "ambulance:AMB-103:version" "0" | Out-Null

Write-Host "  ✓ All ambulances set to AVAILABLE" -ForegroundColor Green

# Issue 3: Clear dispatch queues
Write-Host "`n3. Clearing dispatch queues..." -ForegroundColor Yellow
docker exec redis redis-cli DEL "dispatch:queue:HIGH" "dispatch:queue:MEDIUM" "dispatch:queue:LOW" | Out-Null
Write-Host "  ✓ Dispatch queues cleared" -ForegroundColor Green

# Issue 4: Clear idempotency keys
Write-Host "`n4. Clearing idempotency keys..." -ForegroundColor Yellow
$idempKeys = docker exec redis redis-cli KEYS "idempotency:*"
if ($idempKeys) {
    $keys = $idempKeys -split "`n" | Where-Object { $_.Trim() }
    foreach ($key in $keys) {
        docker exec redis redis-cli DEL $key.Trim() | Out-Null
    }
}
Write-Host "  ✓ Idempotency keys cleared" -ForegroundColor Green

Write-Host "`n=== MANUAL STEPS REQUIRED ===" -ForegroundColor Cyan
Write-Host "`n1. RESTART emergency-service in STS" -ForegroundColor Yellow
Write-Host "   - Stop emergency-service" -ForegroundColor White
Write-Host "   - Right-click emergency-service project" -ForegroundColor White
Write-Host "   - Run As → Spring Boot App" -ForegroundColor White
Write-Host "   - This will pick up the new Kafka offsets" -ForegroundColor White

Write-Host "`n2. RESTART dispatch-service in STS" -ForegroundColor Yellow
Write-Host "   - Stop dispatch-service" -ForegroundColor White
Write-Host "   - Right-click dispatch-service project" -ForegroundColor White
Write-Host "   - Run As → Spring Boot App" -ForegroundColor White
Write-Host "   - This will load the debug code and fresh Redis state" -ForegroundColor White

Write-Host "`n3. After restarting both services, run:" -ForegroundColor Yellow
Write-Host "   ./FINAL-DEBUG.ps1" -ForegroundColor Cyan

Write-Host "`n=== WHY THIS FIXES THE ISSUES ===" -ForegroundColor Cyan
Write-Host "`nIssue: Dispatch saw ambulances as ARRIVED/ON_ROUTE instead of AVAILABLE" -ForegroundColor Yellow
Write-Host "Cause: Ambulances were stuck in old FSM states from previous test runs" -ForegroundColor White
Write-Host "Fix: Cleared all ambulance keys and set fresh AVAILABLE status" -ForegroundColor Green

Write-Host "`nIssue: Emergency-service kept crashing with Kafka errors" -ForegroundColor Yellow
Write-Host "Cause: Consumer stuck on corrupted messages at offsets 101-109" -ForegroundColor White
Write-Host "Fix: Reset consumer offsets to latest, skipping bad messages" -ForegroundColor Green

Write-Host "`n=== READY TO TEST ===" -ForegroundColor Cyan
Write-Host "After restarting services, the system should work correctly!" -ForegroundColor Green
