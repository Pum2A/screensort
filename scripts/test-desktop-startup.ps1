param([switch]$CoreOnly, [switch]$IdentityOnly)

# Synthetic data only. Never launch the installed app against the owner's profile.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$stamp = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6)
$sandbox = Join-Path $repo ".release/startup-check-$stamp"
$data = Join-Path $sandbox 'data'
$builtRuntime = Join-Path $repo 'frontend/src-tauri/target/release/runtime'
$binary = Join-Path $repo 'frontend/src-tauri/target/release/screensort-desktop.exe'
$checkUser = $null
$previousSigningKey = $env:TAURI_SIGNING_PRIVATE_KEY
[IO.Directory]::CreateDirectory($sandbox) | Out-Null
try {
    # Test children do not need the installer-signing credential.
    $env:TAURI_SIGNING_PRIVATE_KEY = $null
    $identity = @{}
    if ($env:GITHUB_ACTIONS -eq 'true' -and $env:RUNNER_ENVIRONMENT -eq 'github-hosted') {
        $userName = 'sscheck' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
        $password = ConvertTo-SecureString ('S!9a' + [Guid]::NewGuid().ToString('N')) -AsPlainText -Force
        $checkUser = New-LocalUser -Name $userName -Password $password -AccountNeverExpires -UserMayNotChangePassword
        Add-LocalGroupMember -SID 'S-1-5-32-545' -Member $checkUser
        & icacls.exe $sandbox /grant "*$($checkUser.SID.Value):(OI)(CI)M" /Q
        if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare the isolated standard-user sandbox' }
        if ((Get-Service -Name seclogon).StartType -eq 'Disabled') { Set-Service -Name seclogon -StartupType Manual }
        Start-Service -Name seclogon
        $identity.Credential = New-Object Management.Automation.PSCredential ("$env:COMPUTERNAME\$userName", $password)
        $identity.LoadUserProfile = $true
        # Start-Process inherits the caller environment, even with another credential.
        # In particular, the new account cannot use runneradmin's private TEMP folder.
        $temp = Join-Path $sandbox 'temp'
        [IO.Directory]::CreateDirectory($temp) | Out-Null
        $identity.Environment = @{ TEMP = $temp; TMP = $temp; TAURI_SIGNING_PRIVATE_KEY = $null }
        $identityOutput = Join-Path $sandbox 'identity.txt'
        $probe = Start-Process -FilePath (Join-Path $env:SystemRoot 'System32/whoami.exe') -ArgumentList '/user' -WorkingDirectory $sandbox -WindowStyle Hidden -PassThru -Wait -RedirectStandardOutput $identityOutput @identity
        if ($probe.ExitCode -ne 0 -or (Get-Content -LiteralPath $identityOutput -Raw) -notmatch [regex]::Escape($checkUser.SID.Value)) { throw 'Cannot verify the standard-user test identity' }
        Write-Host 'Standard-user process identity and writable TEMP prepared.'
    }
    if ($IdentityOnly) { return }
    if (-not (Test-Path -LiteralPath $binary)) { throw 'Build the desktop binary before its startup check' }
    if ($CoreOnly -and ((Test-Path -LiteralPath (Join-Path $builtRuntime 'python')) -or (Test-Path -LiteralPath (Join-Path $builtRuntime 'models')))) { throw 'CoreOnly build contains stale AI resources' }
    # The bundler patches the original executable later. Run an identical copy
    # beside it so this still tests the actual Tauri resource resolver.
    $checkExe = Join-Path $repo "frontend/src-tauri/target/release/screensort-startup-check-$stamp.exe"
    Copy-Item -LiteralPath $binary -Destination $checkExe
    $arguments = @('--startup-check', '--data-dir', ('"' + $data + '"'))
    if ($CoreOnly) { $arguments += '--catalog-only' }
    $started = Get-Date
    $process = Start-Process -FilePath $checkExe -ArgumentList $arguments -WorkingDirectory ([IO.Path]::GetDirectoryName($checkExe)) -WindowStyle Hidden -PassThru -RedirectStandardOutput "$data.stdout.log" -RedirectStandardError "$data.stderr.log" @identity
    # Retain the native handle while the process is alive. Without it, Windows
    # PowerShell can return a null ExitCode after this asynchronous process exits.
    $null = $process.Handle
    if (-not $process.WaitForExit(300000)) {
        # This handle belongs exclusively to the just-created isolated test.
        $process.Kill()
        $process.WaitForExit()
        throw 'Desktop startup check exceeded five minutes; no installer may be published'
    }
    $process.Refresh()
    if ($null -eq $process.ExitCode) { throw 'Cannot determine the desktop startup check exit code' }
    if (-not (Test-Path -LiteralPath "$data.report.json")) { throw "Desktop startup check exited with code $($process.ExitCode) without a report" }
    $report = Get-Content -LiteralPath "$data.report.json" -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $report.passed -or -not $report.tauriResourceResolver) {
        $reason = if ($report.PSObject.Properties['error']) { [string]$report.error } else { 'Tauri resource resolver was not verified' }
        throw "Desktop startup check failed: $reason"
    }
    if ($process.ExitCode -ne 0) { throw "Desktop startup check exited with code $($process.ExitCode)" }
    if ($CoreOnly -and $report.bundledAiPresent) { throw 'CoreOnly startup used a full AI runtime' }
    Write-Host "Desktop startup verified in $([int]((Get-Date) - $started).TotalSeconds)s: scan, thumbnail, persistence, unchanged synthetic source."
} catch {
    # Keep useful diagnostics from our synthetic test, not raw session credentials.
    $logs = @(Get-ChildItem -LiteralPath (Join-Path $data 'sessions') -Recurse -Filter java.log -ErrorAction SilentlyContinue)
    if ($logs.Count) {
        $details = ($logs | ForEach-Object { Get-Content -LiteralPath $_.FullName -Tail 100 -Encoding UTF8 }) -join "`n"
        $details = $details -replace '(?i)\b[0-9a-f]{64}\b', '[redacted]'
        $details = $details -replace '(?i)\b(?:github_pat_|gh[pousr]_)[a-z0-9_]+\b', '[redacted]'
        if ($details.Length -gt 24000) { $details = $details.Substring($details.Length - 24000) }
        [IO.File]::WriteAllText((Join-Path $sandbox 'java-startup-error.txt'), $details, (New-Object Text.UTF8Encoding($false)))
        Write-Host $details
    }
    throw
} finally {
    $env:TAURI_SIGNING_PRIVATE_KEY = $previousSigningKey
    if ($checkUser) { Remove-LocalUser -SID $checkUser.SID }
}
