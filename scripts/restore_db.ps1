# ==============================================================================
# SCRIPT PHỤC HỒI DỮ LIỆU CỤC BỘ (LOCAL RESTORE) - POWERSHELL
# ==============================================================================
param (
    [string]$FilePath
)

$containerName = "kcht_postgres"
$dbUser = "kcht_user"
$dbName = "kcht_db"
$backupDir = "$PSScriptRoot\..\backups"

if (-not $FilePath) {
    # Lấy tệp backup mới nhất trong thư mục backups
    $latestBackup = Get-ChildItem "$backupDir\*.sql" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latestBackup) {
        Write-Error "Khong tim thay ban sao luu nao trong $backupDir!"
        exit 1
    }
    $FilePath = $latestBackup.FullName
}

if (-not (Test-Path $FilePath)) {
    Write-Error "Tep sao luu khong ton tai: $FilePath"
    exit 1
}

Write-Host ">>> Dang phuc hoi database tu: $FilePath..." -ForegroundColor Cyan
Get-Content $FilePath | docker exec -i $containerName psql -U $dbUser -d $dbName

if ($LASTEXITCODE -eq 0) {
    Write-Host ">>> Phuc hoi du lieu thanh cong!" -ForegroundColor Green
} else {
    Write-Error ">>> Co loi xay ra trong qua trinh phuc hoi!"
}
