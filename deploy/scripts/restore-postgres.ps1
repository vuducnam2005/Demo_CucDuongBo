# ==============================================================================
# SCRIPT POWERSHELL PHỤC HỒI CƠ SỞ DỮ LIỆU POSTGRESQL (WINDOWS / LOCAL)
# ==============================================================================
param(
    [Parameter(Mandatory=$true)]
    [string]$BackupFile,
    [string]$ContainerName = "kcht_prod_postgres",
    [string]$DbName = "kcht_prod_db",
    [string]$DbUser = "kcht_prod_admin",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    Write-Host "[ERROR] Không tìm thấy tệp sao lưu: $BackupFile" -ForegroundColor Red
    exit 1
}

# Kiểm tra container có đang chạy không
$running = docker ps --format '{{.Names}}' | Select-String "^$ContainerName$"
if ($null -eq $running) {
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

# 1. Kiểm tra mã băm SHA-256 nếu có tệp .sha256 đi kèm
$shaFile = "$BackupFile.sha256"
if (Test-Path $shaFile) {
    Write-Host "[INFO] Đang xác thực mã băm SHA-256..." -ForegroundColor Yellow
    $expectedHash = (Get-Content $shaFile).Trim()
    $actualHash = (Get-FileHash -Path $BackupFile -Algorithm SHA256).Hash
    if ($expectedHash -ne $actualHash) {
        Write-Host "[ERROR] Mã băm SHA-256 không khớp! Tệp có dấu hiệu bị hỏng hoặc sửa đổi." -ForegroundColor Red
        exit 2
    }
    Write-Host "[SUCCESS] Mã băm SHA-256 hợp lệ tuyệt đối." -ForegroundColor Green
}

# 2. Ngắt kết nối người dùng hiện tại
Write-Host "[INFO] Đang ngắt các kết nối active vào CSDL $DbName..." -ForegroundColor Yellow
docker exec $ContainerName psql -U $DbUser -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DbName' AND pid <> pg_backend_pid();"

# 3. Đưa tệp vào container
$targetContainerPath = "/backups/restore_temp.dump"
docker exec $ContainerName mkdir -p /backups
docker cp $BackupFile "$ContainerName`:$targetContainerPath"

# 4. Phục hồi dữ liệu
Write-Host "[INFO] Đang thực thi pg_restore..." -ForegroundColor Yellow
docker exec $ContainerName pg_restore -U $DbUser -d $DbName --clean --if-exists --no-owner --no-privileges -v $targetContainerPath

# 5. Cập nhật thống kê
Write-Host "[INFO] Đang thực thi VACUUM ANALYZE..." -ForegroundColor Yellow
docker exec $ContainerName psql -U $DbUser -d $DbName -c "VACUUM ANALYZE;"

# 6. Dọn dẹp
docker exec $ContainerName rm -f $targetContainerPath

$count = docker exec $ContainerName psql -U $DbUser -d $DbName -t -A -c "SELECT COUNT(*) FROM raw_dataset_record;"
Write-Host "[SUCCESS] Phục hồi CSDL thành công! Tổng số bản ghi raw_dataset_record: $count" -ForegroundColor Green
