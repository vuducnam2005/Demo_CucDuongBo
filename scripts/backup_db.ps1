# ==============================================================================
# SCRIPT SAO LƯU DỮ LIỆU CỤC BỘ (LOCAL BACKUP) - POWERSHELL
# ==============================================================================

$containerName = "kcht_postgres"
$dbUser = "kcht_user"
$dbName = "kcht_db"
$backupDir = "$PSScriptRoot\..\backups"

if (!(Test-Path $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = "$backupDir\kcht_db_backup_$timestamp.sql"

Write-Host ">>> Dang thuc hien sao luu database $dbName..." -ForegroundColor Cyan
docker exec -t $containerName pg_dump -U $dbUser -d $dbName --clean --if-exists --no-owner --no-acl > $backupFile

if ($LASTEXITCODE -eq 0 -and (Test-Path $backupFile)) {
    $fileSize = (Get-Item $backupFile).Length
    Write-Host ">>> Sao luu thanh cong! Tep duoc luu tai: $backupFile (Kich thuoc: $fileSize bytes)" -ForegroundColor Green
} else {
    Write-Error ">>> Sao luu that bai!"
}
