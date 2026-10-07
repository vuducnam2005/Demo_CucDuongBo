# ==============================================================================
# SCRIPT POWERSHELL SAO LƯU MINIO S3 BUCKETS (WINDOWS / LOCAL)
# ==============================================================================
param(
    [string]$HostBackupDir = "deploy\backups\minio"
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$archiveFile = Join-Path $HostBackupDir "minio_buckets_$timestamp.zip"
$tempDir = Join-Path $HostBackupDir "temp_$timestamp"

if (-not (Test-Path $HostBackupDir)) {
    New-Item -ItemType Directory -Path $HostBackupDir -Force | Out-Null
}

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "[INFO] Bắt đầu sao lưu vùng lưu trữ MinIO S3..." -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

# Kiểm tra container minio_init
$mcContainer = "kcht_prod_minio_init"
$running = docker ps --format '{{.Names}}' | Select-String "^$mcContainer$"
if ($null -eq $running) {
    $fallback = docker ps --format '{{.Names}}' | Select-String "^kcht_minio$"
    if ($null -ne $fallback) {
        Write-Host "[INFO] Sao chép trực tiếp từ dữ liệu volume MinIO..." -ForegroundColor Yellow
        docker cp "kcht_minio:/data" $tempDir
    } else {
        Write-Host "[ERROR] Không tìm thấy container MinIO đang chạy!" -ForegroundColor Red
        exit 1
    }
} else {
    docker exec $mcContainer /bin/sh -c "/usr/bin/mc mirror --overwrite myminio/kcht-documents /tmp/backup_minio/kcht-documents"
    docker cp "$mcContainer`:/tmp/backup_minio" $tempDir
    docker exec $mcContainer rm -rf /tmp/backup_minio
}

Write-Host "[INFO] Đang đóng gói tệp nén ZIP..." -ForegroundColor Yellow
Compress-Archive -Path "$tempDir\*" -DestinationPath $archiveFile -Force
Remove-Item -Recurse -Force $tempDir

$hash = Get-FileHash -Path $archiveFile -Algorithm SHA256
$hash.Hash | Out-File -FilePath "$archiveFile.sha256" -Encoding ascii

$fileSize = (Get-Item $archiveFile).Length / 1MB
Write-Host "[SUCCESS] Sao lưu MinIO S3 thành công: $archiveFile ($([math]::Round($fileSize, 2)) MB)" -ForegroundColor Green
