# ==============================================================================
# SCRIPT KIỂM TRA POSTGIS BẰNG POWERSHELL QUA DOCKER
# ==============================================================================

$containerName = "kcht_postgres"
$dbUser = "kcht_user"
$dbName = "kcht_db"

Write-Host ">>> Dang kiem tra trang thai container $containerName..." -ForegroundColor Cyan

$status = docker inspect -f '{{.State.Health.Status}}' $containerName 2>$null
if ($status -ne "healthy") {
    Write-Warning "Container $containerName chua san sang (Trang thai hien tai: $status). Vui long chay: docker compose up -d"
    exit 1
}

Write-Host ">>> Thuc thi kiem tra PostGIS qua file scripts/test_postgis.sql..." -ForegroundColor Green
Get-Content "$PSScriptRoot\test_postgis.sql" | docker exec -i $containerName psql -U $dbUser -d $dbName

Write-Host "`n>>> Kiem tra hoan tat!" -ForegroundColor Green
