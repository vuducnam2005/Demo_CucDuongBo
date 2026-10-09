param([string]$patchFile)
$p = [System.IO.File]::ReadAllText($patchFile, [System.Text.Encoding]::UTF8)
$info = [System.Diagnostics.ProcessStartInfo]::new()
$info.FileName = (Get-Command codex.exe).Source
$info.WorkingDirectory = (Get-Location).Path
$info.Arguments = '--codex-run-as-apply-patch "' + $p.Replace('"', '\"') + '"'
$info.UseShellExecute = $false
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$proc = [System.Diagnostics.Process]::Start($info)
$out = $proc.StandardOutput.ReadToEnd()
$err = $proc.StandardError.ReadToEnd()
$proc.WaitForExit()
Write-Output $out
if ($err) { Write-Error $err }
if ($proc.ExitCode -ne 0) { throw "patch exit $($proc.ExitCode)" }