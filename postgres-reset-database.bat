@echo off
echo ========================================
echo Resetting PostgreSQL Database
echo ========================================
echo.

echo Step 1: Dropping all tables...
docker exec -i postgres psql -U dispatch_user -d emergency_dispatch -c "DROP TABLE IF EXISTS assignment_history CASCADE;"
docker exec -i postgres psql -U dispatch_user -d emergency_dispatch -c "DROP TABLE IF EXISTS state_transitions CASCADE;"
docker exec -i postgres psql -U dispatch_user -d emergency_dispatch -c "DROP TABLE IF EXISTS emergencies CASCADE;"
docker exec -i postgres psql -U dispatch_user -d emergency_dispatch -c "DROP TABLE IF EXISTS ambulances CASCADE;"
echo.

echo Step 2: Running initialization script...
docker exec -i postgres psql -U dispatch_user -d emergency_dispatch < init-db/01-init.sql
echo.

echo ========================================
echo Database reset complete!
echo ========================================
echo.
echo You can now restart your dispatch-service.
echo.
pause
