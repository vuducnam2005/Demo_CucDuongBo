# ==============================================================================
# SCRIPT POWERSHELL PHỤC HỒI MINIO S3 BUCKETS (WINDOWS / LOCAL)
# ==============================================================================
param(
    [Parameter(Mandatory=$true)]
    [string]$ArchiveFile
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ArchiveFile)) {
    Write-Host "[ERROR] Không tìm thấy tệp sao lưu: $ArchiveFile" -ForegroundColor Red
    exit 1
}

$shaFile = "$ArchiveFile.sha256"
if (Test-Path $shaFile) {
    Write-Host "[INFO] Đang xác thực mã băm SHA-256..." -ForegroundColor Yellow
    $expectedHash = (Get-Content $shaFile).Trim()
    $actualHash = (Get-FileHash -Path $ArchiveFile -Algorithm SHA256).Hash
    if ($expectedHash -ne $actualHash) {
        Write-Host "[ERROR] Mã băm SHA-256 không khớp! Tệp có dấu hiệu bị hỏng." -ForegroundColor Red
        exit 2
    }
    Write-Host "[SUCCESS] Mã băm SHA-256 hợp lệ." -ForegroundColor Green
}

$tempExtract = Join-Path (Split-Path $ArchiveFile) "restore_temp_$(Get-Date -Format 'HHmmss')"
New-Item -ItemType Directory -Path $tempExtract -Force | Out-Null

Write-Host "[INFO] Đang giải nén tệp lưu trữ..." -ForegroundColor Yellow
Expand-Archive -Path $ArchiveFile -DestinationPath $tempExtract -Force

# Đưa dữ liệu vào container
Write-Host "[INFO] Đang phục hồi dữ liệu vào container kcht_minio..." -ForegroundColor Yellow
docker cp "$tempExtract\." "kcht_minio:/data/"

Remove-Item -Recurse -Force $tempExtract
Write-Host "[SUCCESS] Phục hồi vùng lưu trữ MinIO thành công!" -ForegroundColor Green
