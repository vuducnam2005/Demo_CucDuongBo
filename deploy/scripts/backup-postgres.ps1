# ==============================================================================
# SCRIPT POWERSHELL SAO LƯU CƠ SỞ DỮ LIỆU POSTGRESQL (WINDOWS / LOCAL)
# ==============================================================================
param(
    [string]$ContainerName = "kcht_prod_postgres",
    [string]$DbName = "kcht_prod_db",
    [string]$DbUser = "kcht_prod_admin",
    [string]$HostBackupDir = "deploy\backups\postgres"
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFilename = "kcht_db_backup_$timestamp.dump"
$hostBackupPath = Join-Path $HostBackupDir $backupFilename
$containerBackupPath = "/backups/$backupFilename"

if (-not (Test-Path $HostBackupDir)) {
    New-Item -ItemType Directory -Path $HostBackupDir -Force | Out-Null
}

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "[INFO] Bắt đầu sao lưu CSDL PostgreSQL: $DbName" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

# Kiểm tra container có đang chạy không
$running = docker ps --format '{{.Names}}' | Select-String "^$ContainerName$"
if ($null -eq $running) {
    # Nếu container prod chưa chạy, thử kiểm tra container local kcht_postgres
    $fallback = docker ps --format '{{.Names}}' | Select-String "^kcht_postgres$"
    if ($null -ne $fallback) {
        $ContainerName = "kcht_postgres"
        $DbName = "kcht_db"
        $DbUser = "kcht_user"
        Write-Host "[INFO] Chuyển hướng sang container cục bộ: $ContainerName" -ForegroundColor Yellow
    } else {
        Write-Host "[ERROR] Không tìm thấy container $ContainerName đang chạy!" -ForegroundColor Red
        exit 1
    }
}

Write-Host "[INFO] Đang chuẩn bị thư mục sao lưu trong container..." -ForegroundColor Yellow
docker exec $ContainerName mkdir -p /backups

Write-Host "[INFO] Đang thực thi pg_dump (-Fc, compression 9)..." -ForegroundColor Yellow
docker exec $ContainerName pg_dump -U $DbUser -d $DbName -F c -b -v -Z 9 -f $containerBackupPath

Write-Host "[INFO] Sao chép tệp dump ra máy chủ host..." -ForegroundColor Yellow
docker cp "$ContainerName`:$containerBackupPath" $hostBackupPath

Write-Host "[INFO] Tạo mã băm SHA-256..." -ForegroundColor Yellow
$hash = Get-FileHash -Path $hostBackupPath -Algorithm SHA256
$hash.Hash | Out-File -FilePath "$hostBackupPath.sha256" -Encoding ascii

$fileSize = (Get-Item $hostBackupPath).Length / 1MB
Write-Host "[SUCCESS] Sao lưu CSDL thành công!" -ForegroundColor Green
Write-Host " - Tệp sao lưu: $hostBackupPath ($([math]::Round($fileSize, 2)) MB)" -ForegroundColor Green
Write-Host " - Mã băm SHA-256: $($hash.Hash)" -ForegroundColor Green
