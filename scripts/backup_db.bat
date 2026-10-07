@echo off
setlocal enabledelayedexpansion

echo ==============================================================================
echo SAO LUU DATABASE KCHT (CMD/BAT)
echo ==============================================================================

set CONTAINER_NAME=kcht_postgres
set DB_USER=kcht_user
set DB_NAME=kcht_db
set BACKUP_DIR=%~dp0..\backups

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set TIMESTAMP=%datetime:~0,8%_%datetime:~8,6%
set BACKUP_FILE=%BACKUP_DIR%\kcht_db_backup_%TIMESTAMP%.sql

echo Dang sao luu database %DB_NAME% vao %BACKUP_FILE% ...
docker exec -t %CONTAINER_NAME% pg_dump -U %DB_USER% -d %DB_NAME% --clean --if-exists --no-owner --no-acl > "%BACKUP_FILE%"

if %ERRORLEVEL% equ 0 (
    echo Sao luu thanh cong!
) else (
    echo Sao luu that bai!
)
pause
