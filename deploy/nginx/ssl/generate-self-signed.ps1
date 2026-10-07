# ==============================================================================
# Script PowerShell sinh chứng chỉ TLS Self-Signed cho Windows
# ==============================================================================
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$certPath = Join-Path $PSScriptRoot "kcht_tls.crt"
$keyPath = Join-Path $PSScriptRoot "kcht_tls.key"

if ((Test-Path $certPath) -and (Test-Path $keyPath)) {
    Write-Host "[INFO] Chứng chỉ TLS đã tồn tại tại $PSScriptRoot." -ForegroundColor Green
    exit 0
}

Write-Host "[INFO] Đang sinh cặp khóa RSA và chứng chỉ tự ký X.509..." -ForegroundColor Yellow

$openssl = Get-Command openssl -ErrorAction SilentlyContinue
if ($null -ne $openssl) {
    & openssl req -x509 -nodes -days 365 -newkey rsa:2048 `
      -keyout $keyPath `
      -out $certPath `
      -subj "/C=VN/ST=HaNoi/L=HaNoi/O=CucDuongBoVietNam/OU=KCHT/CN=kcht.drvn.gov.vn" `
      -addext "subjectAltName=DNS:kcht.drvn.gov.vn,DNS:localhost,IP:127.0.0.1"
    Write-Host "[SUCCESS] Đã sinh thành công chứng chỉ TLS bằng openssl." -ForegroundColor Green
} else {
    Write-Host "[WARN] Không tìm thấy openssl trong PATH. Tạo placeholder để Nginx có thể khởi động." -ForegroundColor Yellow
    # Create empty placeholder certs if openssl is not present
    "-----BEGIN CERTIFICATE-----`nPLACEHOLDER_CERT`n-----END CERTIFICATE-----" | Out-File -FilePath $certPath -Encoding ascii
    "-----BEGIN PRIVATE KEY-----`nPLACEHOLDER_KEY`n-----END PRIVATE KEY-----" | Out-File -FilePath $keyPath -Encoding ascii
}
