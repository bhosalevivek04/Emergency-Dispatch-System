@echo off
echo ========================================
echo Start All Services
echo ========================================
echo.
echo This will guide you to start all microservices.
echo.
echo IMPORTANT: Start services in this order:
echo   1. Ambulance Service (FIRST - broadcasts locations)
echo   2. Wait 30 seconds
echo   3. Dispatch Service (consumes locations)
echo   4. Other services (any order)
echo.
pause

echo.
echo Step 1: Start Ambulance Service
echo -----------------------------------------
echo Open Terminal 1 and run:
echo   cd ambulance-service
echo   mvnw spring-boot:run
echo.
echo Wait for: "Initialized ambulance AMB-101..."
echo Then wait 30 MORE seconds for broadcasting.
echo.
pause

echo.
echo Step 2: Start Dispatch Service
echo -----------------------------------------
echo Open Terminal 2 and run:
echo   cd dispatch-service
echo   mvnw spring-boot:run
echo.
echo Wait for: "Location consumed ambulanceId=AMB-XXX"
echo.
pause

echo.
echo Step 3: Start Emergency Service
echo -----------------------------------------
echo Open Terminal 3 and run:
echo   cd emergency-service
echo   mvnw spring-boot:run
echo.
pause

echo.
echo Step 4: Start Tracking Service
echo -----------------------------------------
echo Open Terminal 4 and run:
echo   cd tracking-service
echo   mvnw spring-boot:run
echo.
pause

echo.
echo Step 5: Start API Gateway
echo -----------------------------------------
echo Open Terminal 5 and run:
echo   cd api-gateway
echo   mvnw spring-boot:run
echo.
pause

echo.
echo Step 6: Start Frontend (Optional)
echo -----------------------------------------
echo Open Terminal 6 and run:
echo   cd tracking-client
echo   npm start
echo.
echo This will open http://localhost:3000
echo.
pause

echo.
echo ========================================
echo All Services Started!
echo ========================================
echo.
echo Test with: test-long-distance.bat
echo.
pause
