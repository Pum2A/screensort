param(
    [Parameter(Mandatory)][string]$Installer,
    [Parameter(Mandatory)][string]$Tag,
    [Parameter(Mandatory)][string]$OutputPath
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$config = Get-Content -LiteralPath (Join-Path $repo 'frontend/src-tauri/tauri.conf.json') -Raw | ConvertFrom-Json
$file = Get-Item -LiteralPath $Installer
if ($file.Name -ne "ScreenSort_$($config.version)_x64-setup.exe") { throw 'Installer name does not match app version' }
if ($file.Length -gt 512MB -or $file.Length -lt 1MB) { throw 'Unexpected Core installer size' }
if ($Tag -ne "v$($config.version)-preview") { throw 'Release tag must match the exact app version' }
$signature = (Get-Content -LiteralPath ($file.FullName + '.sig') -Raw).Trim()
$decoded = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($signature))
if ($decoded -notmatch ('(?m)^trusted comment:.*\bversion:' + [regex]::Escape($config.version) + '(?:\s|$)')) {
    throw 'Signature must bind the exact app version; old signatures are not suitable for updates'
}
& cargo run --quiet --release --locked --manifest-path (Join-Path $repo 'frontend/src-tauri/Cargo.toml') --example verify-update -- $file.FullName ($file.FullName + '.sig') $config.version
if ($LASTEXITCODE -ne 0) { throw 'Cryptographic verification failed. Refusing to prepare the update manifest.' }
$notes = Get-Content -LiteralPath (Join-Path $repo 'packaging/distribution/core-release.md') -Raw
$manifest = [ordered]@{version = $config.version; notes = $notes; pub_date = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'); platforms = @{
    'windows-x86_64' = @{signature = $signature; url = "https://github.com/Pum2A/screensort-releases/releases/download/$Tag/$($file.Name)"}
}}
$destination = [IO.Path]::GetFullPath($OutputPath)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
[IO.File]::WriteAllText($destination, ($manifest | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
Write-Host "Update manifest prepared for $($config.version); no private signing key included."
