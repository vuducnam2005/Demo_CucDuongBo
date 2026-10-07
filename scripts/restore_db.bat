@echo off
setlocal enabledelayedexpansion

echo ==============================================================================
echo PHUC HOI DATABASE KCHT (CMD/BAT)
echo ==============================================================================

set CONTAINER_NAME=kcht_postgres
set DB_USER=kcht_user
set DB_NAME=kcht_db
set BACKUP_FILE=%1

if "%BACKUP_FILE%"=="" (
    echo Vui long truyen duong dan file backup: restore_db.bat [duong_dan_file.sql]
    pause
    exit /b 1
)

echo Dang phuc hoi tu %BACKUP_FILE% ...
type "%BACKUP_FILE%" | docker exec -i %CONTAINER_NAME% psql -U %DB_USER% -d %DB_NAME%

if %ERRORLEVEL% equ 0 (
    echo Phuc hoi thanh cong!
) else (
    echo Co loi xay ra trong qua trinh phuc hoi!
)
pause
