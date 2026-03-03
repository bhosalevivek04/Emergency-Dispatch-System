@echo off
echo ========================================
echo Starting PostgreSQL + PostGIS
echo ========================================
echo.

echo Stopping any existing postgres container...
docker stop postgres 2>nul
docker rm postgres 2>nul

echo.
echo Starting PostgreSQL with PostGIS...
docker-compose up -d postgres

echo.
echo Waiting for PostgreSQL to be ready...
timeout /t 10 /nobreak >nul

echo.
echo Checking PostgreSQL status...
docker exec postgres pg_isready -U dispatch_user -d emergency_dispatch

echo.
echo ========================================
echo PostgreSQL is ready!
echo ========================================
echo.
echo Connection details:
echo   Host: localhost
echo   Port: 5432
echo   Database: emergency_dispatch
echo   Username: dispatch_user
echo   Password: dispatch_password
echo.
echo To connect using psql:
echo   docker exec -it postgres psql -U dispatch_user -d emergency_dispatch
echo.
echo To view logs:
echo   docker logs postgres
echo.
echo To stop:
echo   docker-compose stop postgres
echo.
