# Start Infrastructure Services for Testing
# This script starts only PostgreSQL (required for auth-service)
# Kafka and Redis are optional for auth-service testing

Write-Host "Starting Infrastructure Services..." -ForegroundColor Cyan
Write-Host ""

# Check if Docker is running
try {
    docker ps | Out-Null
    Write-Host "✓ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "✗ Docker is not running. Please start Docker Desktop first." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Starting PostgreSQL (required for auth-service)..." -ForegroundColor Yellow
docker-compose up -d postgres

Write-Host ""
Write-Host "Starting Redis (optional - for other services)..." -ForegroundColor Yellow
docker-compose up -d redis

Write-Host ""
Write-Host "Starting Kafka (optional - for other services)..." -ForegroundColor Yellow
docker-compose up -d kafka

Write-Host ""
Write-Host "Waiting for services to be healthy..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

Write-Host ""
Write-Host "Checking service status..." -ForegroundColor Cyan
docker-compose ps

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "Infrastructure Services Started!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Services running:"
Write-Host "  PostgreSQL: localhost:5432" -ForegroundColor White
Write-Host "    Database: emergency_dispatch"
Write-Host "    Username: dispatch_user"
Write-Host "    Password: dispatch_password"
Write-Host ""
Write-Host "  Redis: localhost:6379" -ForegroundColor White
Write-Host "  Kafka: localhost:9092" -ForegroundColor White
Write-Host ""
Write-Host "To stop services: docker-compose down" -ForegroundColor Yellow
Write-Host "To view logs: docker-compose logs -f [service-name]" -ForegroundColor Yellow
