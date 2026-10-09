param([switch]$Shared)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repository = Split-Path -Parent $PSScriptRoot
$environmentPath = Join-Path $repository '.env'
if (-not (Test-Path -LiteralPath $environmentPath)) {
    throw 'Missing local .env. Create it from .env.example.'
}

$original = [System.IO.File]::ReadAllText($environmentPath, [System.Text.Encoding]::UTF8)
$databaseUser = [regex]::Match($original, '(?m)^POSTGRES_USER=([A-Za-z0-9_]+)\r?$').Groups[1].Value
$databaseName = [regex]::Match($original, '(?m)^POSTGRES_DB=([A-Za-z0-9_]+)\r?$').Groups[1].Value
if (-not $databaseUser -or -not $databaseName) {
    throw 'POSTGRES_USER or POSTGRES_DB is missing or invalid.'
}

function New-RandomSecret {
    $bytes = New-Object byte[] 30
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes).Replace('+', '-').Replace('/', '_').TrimEnd('=')
}

function Invoke-Database([string]$statement) {
    $result = $statement | docker exec -i kcht_postgres psql -X -qAt -v ON_ERROR_STOP=1 -U $databaseUser -d $databaseName
    if ($LASTEXITCODE -ne 0) { throw 'Cannot update local PostgreSQL.' }
    return ($result | Out-String).Trim()
}

$accounts = [ordered]@{
    admin = 'DEMO_ADMIN_PASSWORD'
    editor_demo = 'DEMO_EDITOR_PASSWORD'
    manager_demo = 'DEMO_MANAGER_PASSWORD'
    viewer_demo = 'DEMO_VIEWER_PASSWORD'
}
$oldHashes = @{}
$passwords = @{}
$sharedPassword = $null
if ($Shared) {
    $secure = Read-Host 'Shared local demo password (hidden)' -AsSecureString
    $sharedPassword = [System.Net.NetworkCredential]::new('', $secure).Password
    if ($sharedPassword.Length -lt 10 -or $sharedPassword.Contains([char]10) -or $sharedPassword.Contains([char]13)) {
        throw 'Shared local demo password needs at least 10 characters without line breaks.'
    }
}
foreach ($name in $accounts.Keys) {
    $hash = Invoke-Database "SELECT password_hash FROM app_user WHERE username = '$name';"
    if (-not $hash) { throw "Missing local demo account $name." }
    $oldHashes[$name] = $hash
    $passwords[$name] = if ($Shared) { $sharedPassword } else { New-RandomSecret }
}

$updated = $original.TrimEnd("`r", "`n")
foreach ($name in $accounts.Keys) {
    $key = $accounts[$name]
    $updated = [regex]::Replace($updated, "(?m)^$key=[^`r`n]*`r?`n?", '')
    $updated += "`n$key=$($passwords[$name])"
}
$updated += "`n"
$temporaryPath = Join-Path $repository '.env.rotate.local'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = @()
try {
    [System.IO.File]::WriteAllText($temporaryPath, $updated, $utf8)
    Move-Item -LiteralPath $temporaryPath -Destination $environmentPath -Force
    foreach ($name in $accounts.Keys) {
        $plain = $passwords[$name]
        $result = Invoke-Database "UPDATE app_user SET password_hash = crypt('$plain', gen_salt('bf', 12)), updated_at = CURRENT_TIMESTAMP WHERE username = '$name' RETURNING username;"
        if ($result -ne $name) { throw "Cannot update local demo account $name." }
        $changed += $name
    }
    Write-Host 'Updated all four local demo accounts and saved their passwords to ignored .env. Open that file locally; never commit it.'
} catch {
    foreach ($name in $changed) {
        $null = Invoke-Database "UPDATE app_user SET password_hash = '$($oldHashes[$name])', updated_at = CURRENT_TIMESTAMP WHERE username = '$name';"
    }
    [System.IO.File]::WriteAllText($temporaryPath, $original, $utf8)
    Move-Item -LiteralPath $temporaryPath -Destination $environmentPath -Force
    throw
} finally {
    if (Test-Path -LiteralPath $temporaryPath) { Remove-Item -LiteralPath $temporaryPath -Force }
    Remove-Variable passwords, oldHashes, sharedPassword, secure -ErrorAction SilentlyContinue
}
