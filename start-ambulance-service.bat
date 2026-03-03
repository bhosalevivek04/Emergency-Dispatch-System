@echo off
echo ========================================
echo Starting Ambulance Service
echo ========================================
echo.

cd ambulance-service

echo Building ambulance service...
call mvnw.cmd clean install -DskipTests
echo.

echo Starting ambulance service on port 8082...
start "Ambulance Service" cmd /k "mvnw.cmd spring-boot:run"

echo.
echo ========================================
echo Ambulance Service is starting...
echo Check the new window for logs
echo ========================================
echo.

cd ..
pause
