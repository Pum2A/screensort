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
# Get-Content's string has PowerShell provider metadata (PSPath, PSDrive, etc.).
# ConvertTo-Json serializes that metadata unless the value is a plain .NET string.
$notes = [IO.File]::ReadAllText((Join-Path $repo 'packaging/distribution/core-release.md'))
$manifest = [ordered]@{version = $config.version; notes = $notes; pub_date = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'); platforms = @{
    'windows-x86_64' = @{signature = $signature; url = "https://github.com/Pum2A/screensort-releases/releases/download/$Tag/$($file.Name)"}
}}
$json = $manifest | ConvertTo-Json -Depth 5
# Fail closed if serialization ever reintroduces PowerShell provider metadata.
if ((ConvertFrom-Json -InputObject $json).notes -isnot [string]) {
    throw 'Release notes must serialize as plain text; refusing to publish workstation metadata.'
}
$destination = [IO.Path]::GetFullPath($OutputPath)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
[IO.File]::WriteAllText($destination, $json, (New-Object Text.UTF8Encoding($false)))
Write-Host "Update manifest prepared for $($config.version); no private signing key included."
