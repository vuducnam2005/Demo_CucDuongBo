param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('admin', 'manager_demo', 'editor_demo', 'viewer_demo')]
    [string]$Username
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$password = Read-Host "Mật khẩu MỚI cho $Username (ít nhất 12 ký tự)" -AsSecureString
$plain = [System.Net.NetworkCredential]::new('', $password).Password
try {
    if ($plain.Length -lt 12 -or $plain -match '[\r\n]') {
        throw 'Mật khẩu cần ít nhất 12 ký tự và không có dấu xuống dòng.'
    }
    $escaped = $plain.Replace("'", "''")
    $statement = "UPDATE app_user SET password_hash = crypt('$escaped', gen_salt('bf', 12)), updated_at = now() WHERE username = '$Username' RETURNING username;"
    $result = $statement | docker exec -i kcht_postgres psql -X -v ON_ERROR_STOP=1 -U kcht_user -d kcht_db -qAt
    if ($LASTEXITCODE -ne 0 -or $result.Trim() -ne $Username) {
        throw 'Không cập nhật được mật khẩu. Kiểm tra database và migration trước.'
    }
    Write-Host "Đã đổi mật khẩu cho tài khoản $Username. Không ghi mật khẩu ra log."
}
finally {
    Remove-Variable plain, escaped, statement -ErrorAction SilentlyContinue
}
