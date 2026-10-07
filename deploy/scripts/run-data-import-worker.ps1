# ==============================================================================
# SCRIPT POWERSHELL CHẠY DATA IMPORT WORKER ĐỘC LẬP
# ==============================================================================
param(
    [string]$DataDir = "C:\Data\kcht_json_2026-10-05",
    [int]$BatchSize = 500,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "[INFO] Bắt đầu tác vụ nạp/làm mới dữ liệu hạ tầng (KCHT Import Worker)" -ForegroundColor Cyan
Write-Host "[INFO] Thư mục dữ liệu: $DataDir" -ForegroundColor Cyan
Write-Host "[INFO] Kích thước lô:   $BatchSize" -ForegroundColor Cyan
Write-Host "[INFO] Chế độ thử:      $($DryRun.IsPresent)" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

$dryRunStr = if ($DryRun.IsPresent) { "true" } else { "false" }

# Chạy worker bằng Java cục bộ hoặc Docker
& .\mvnw.bat spring-boot:run "-Dspring-boot.run.arguments=--kcht.worker.mode=true" `
    "-Dspring-boot.run.jvmArguments=-XX:MaxRAMPercentage=75.0 -XX:+UseZGC -XX:+ZGenerational -Dkcht.data.dir=$DataDir -Dkcht.import.batch-size=$BatchSize -Dkcht.import.dry-run=$dryRunStr"

Write-Host "[SUCCESS] Tác vụ worker hoàn tất!" -ForegroundColor Green
