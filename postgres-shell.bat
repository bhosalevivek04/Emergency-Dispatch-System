@echo off
echo ========================================
echo PostgreSQL Interactive Shell
echo ========================================
echo.
echo Connecting to emergency_dispatch database...
echo.
echo Useful commands:
echo   \dt              - List all tables
echo   \d table_name    - Describe table structure
echo   \l               - List all databases
echo   \q               - Quit
echo.
echo ========================================
echo.

docker exec -it postgres psql -U dispatch_user -d emergency_dispatch
