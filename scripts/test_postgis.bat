@echo off
echo ==============================================================================
echo KIEM TRA POSTGIS QUA DOCKER (CMD/BAT)
echo ==============================================================================

set CONTAINER_NAME=kcht_postgres
set DB_USER=kcht_user
set DB_NAME=kcht_db

type "%~dp0test_postgis.sql" | docker exec -i %CONTAINER_NAME% psql -U %DB_USER% -d %DB_NAME%

echo.
echo ==============================================================================
echo KIEM TRA HOAN TAT
echo ==============================================================================
pause
