@echo off
echo ========================================
echo Long Distance Route Test
echo ========================================
echo.
echo This test creates an emergency at a longer distance
echo to see the ambulance follow the route on the map.
echo.
echo Locations:
echo   AMB-101 Start: Shivajinagar (18.5304, 73.8467)
echo   Emergency:     Kothrud (18.5074, 73.8077)
echo   Distance:      ~5 km (real road distance)
echo   Expected Time: ~3-4 minutes (based on OSRM route duration)
echo.
echo NOTE: State transitions are now DYNAMIC based on actual route!
echo   - ON_ROUTE: After 10%% of journey time
echo   - ARRIVED: After 90%% of journey time  
echo   - COMPLETED: After full journey + 30s for patient loading
echo.
pause

echo Step 1: Clear Redis
redis-cli FLUSHALL
echo Redis cleared!
echo.

echo Step 2: Wait 15 seconds for ambulances to initialize
timeout /t 15 /nobreak
echo.

echo Step 3: Check ambulances are ready
redis-cli KEYS "ambulance:AMB-*:status"
echo.

echo Step 4: Create emergency at Kothrud (5km away)
echo -----------------------------------------
echo Creating emergency at Kothrud...
echo Location: (18.5074, 73.8077)
echo.

curl -X POST http://localhost:8080/api/emergencies -H "Content-Type: application/json" -d "{\"emergencyId\":\"EMG-LONG-DISTANCE\",\"lat\":18.5074,\"lon\":73.8077,\"priority\":\"HIGH\"}" 2>nul
echo.
echo Emergency created!
echo.

echo Step 5: Wait 5 seconds for assignment
timeout /t 5 /nobreak
echo.

echo Step 6: Check which ambulance was assigned
echo -----------------------------------------
for /f "delims=" %%a in ('redis-cli GET "ambulance:AMB-101:status"') do set amb101=%%a
for /f "delims=" %%a in ('redis-cli GET "ambulance:AMB-102:status"') do set amb102=%%a
for /f "delims=" %%a in ('redis-cli GET "ambulance:AMB-103:status"') do set amb103=%%a

echo AMB-101: %amb101%
echo AMB-102: %amb102%
echo AMB-103: %amb103%
echo.

set assigned_ambulance=
if not "%amb101%"=="AVAILABLE" set assigned_ambulance=AMB-101
if not "%amb102%"=="AVAILABLE" set assigned_ambulance=AMB-102
if not "%amb103%"=="AVAILABLE" set assigned_ambulance=AMB-103

if "%assigned_ambulance%"=="" (
    echo.
    echo ERROR: No ambulance was assigned!
    echo.
    echo Possible issues:
    echo   1. Dispatch service doesn't have ambulance locations
    echo   2. All ambulances are busy
    echo   3. Emergency not reaching dispatch service
    echo.
    echo Run: diagnose-assignment-issue.bat
    echo.
    pause
    exit /b 1
)

echo.
echo Assigned ambulance: %assigned_ambulance%
echo.

echo Step 7: Monitor movement
echo -----------------------------------------
echo The ambulance will now follow the route for ~3-4 minutes
echo Status will change dynamically based on actual route duration!
echo.
echo OPEN THE MAP:
echo   1. Go to tracking-client folder
echo   2. Run: npm start
echo   3. Open: http://localhost:3000
echo   4. Watch the ambulance move along the route!
echo.
echo Monitoring %assigned_ambulance% status for 3 minutes...
echo.

for /l %%i in (1,1,90) do (
    echo [%%i/90] %assigned_ambulance%: 
    redis-cli GET "ambulance:%assigned_ambulance%:status"
    timeout /t 2 /nobreak >nul
)

echo.
echo ========================================
echo Test Complete!
echo ========================================
echo.
echo Check ambulance service logs for:
echo   - "OSRM route fetched: X waypoints"
echo   - "Moving AMB-101 along route: waypoint X/Y"
echo.
echo Check the map at http://localhost:3000 to see:
echo   - Ambulance marker moving along roads
echo   - Route line from ambulance to emergency
echo   - Real-time position updates
echo.
pause
