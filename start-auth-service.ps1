# Start Auth Service
# This script starts the auth-service after infrastructure is ready

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Starting Auth Service" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Check if PostgreSQL is running
Write-Host "Checking PostgreSQL connection..." -ForegroundColor Yellow
$pgRunning = docker ps --filter "name=postgres" --filter "status=running" --format "{{.Names}}"

if ($pgRunning -eq "postgres") {
    Write-Host "✓ PostgreSQL is running" -ForegroundColor Green
} else {
    Write-Host "✗ PostgreSQL is not running" -ForegroundColor Red
    Write-Host "Please run: .\start-infrastructure.ps1" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Starting auth-service on port 8086..." -ForegroundColor Yellow
Write-Host ""

# Change to auth-service directory and run
Set-Location auth-service

Write-Host "Building and starting service..." -ForegroundColor Cyan
mvn spring-boot:run

# Note: This will block until you press Ctrl+C
