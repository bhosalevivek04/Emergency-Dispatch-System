@echo off
echo ========================================
echo Starting Infrastructure Services
echo ========================================
echo.
echo Starting Kafka, Redis, and OSRM...
echo.

docker-compose up -d

echo.
echo Infrastructure started!
echo.
echo Services:
echo   - Kafka: localhost:9092
echo   - Redis: localhost:6379
echo   - OSRM: localhost:5000
echo.
echo Wait 10 seconds for services to be ready...
timeout /t 10 /nobreak
echo.
echo Ready to start application services!
pause
