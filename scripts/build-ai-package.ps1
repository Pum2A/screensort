param(
    [string]$Runtime = '',
    [string]$Version = '2026-10-19-2',
    [switch]$FinalizeExisting
)

# Distribution-only input: portable runtime, never the repository or application data.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $Runtime) { $Runtime = Join-Path $repo 'frontend/src-tauri/runtime' }
$Runtime = [IO.Path]::GetFullPath($Runtime)
if ($Version -notmatch '^[a-z0-9-]{1,64}$') { throw 'Invalid package version' }
$output = Join-Path $repo ".release/ai-packages/$Version"
if ((Test-Path -LiteralPath $output) -and -not $FinalizeExisting) { throw 'Output exists; choose a new version. No archive was overwritten.' }
$required = @('python/python.exe', 'python/python314.dll', 'python/app/screensort_analysis/launch.py',
    'python/Lib/site-packages/rapidocr/__init__.py', 'python/Lib/site-packages/torch/__init__.py',
    'models/models--florence-community--Florence-2-base-ft/snapshots/0b03b6f15a4a211370fb204aee4e7dd48887ea37/model.safetensors',
    'THIRD-PARTY-NOTICES.md', 'FLORENCE-LICENSE.txt')
foreach ($name in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $Runtime $name) -PathType Leaf)) { throw "Required file missing: $name" }
}
$files = [Collections.Generic.List[IO.FileInfo]]::new()
function Collect-Payload([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse point in package: $Path" }
    if ($item.PSIsContainer) {
        foreach ($child in Get-ChildItem -LiteralPath $Path -Force) {
            if ($child.Name -in @('__pycache__', '.cache', '.locks', '.git')) { continue }
            Collect-Payload $child.FullName
        }
    } elseif ($item.Name -notmatch '\.(pyc|pyo|log|partial)$' -and $item.Name -ne 'direct_url.json') {
        if ($item.Name -match '^\.env($|\.)|database-password|^installed\.json$') { throw 'Private configuration found in payload' }
        $files.Add($item)
    }
}
foreach ($name in @('python', 'models', 'THIRD-PARTY-NOTICES.md', 'FLORENCE-LICENSE.txt', 'runtime-manifest.json')) {
    Collect-Payload (Join-Path $Runtime $name)
}
$names = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($file in $files) {
    $name = $file.FullName.Substring($Runtime.Length + 1).Replace('\', '/')
    if ($name.Length -gt 240 -or -not $names.Add($name)) { throw "Long or colliding path: $name" }
    foreach ($part in $name.Split('/')) {
        if ($part -match '[<>:"|?*\x00-\x1f]' -or $part -match '[. ]$' -or
            $part -match '^(CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³])($|\.)') { throw "Unsafe Windows name: $name" }
    }
}
[IO.Directory]::CreateDirectory($output) | Out-Null
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$asset = "screensort-ai-cpu-windows-x64-$Version.zip"
$destination = Join-Path $output $asset
$unpacked = 0L
if (-not $FinalizeExisting) {
  $zip = [IO.Compression.ZipFile]::Open($destination, [IO.Compression.ZipArchiveMode]::Create)
  try {
    foreach ($file in ($files | Sort-Object FullName)) {
        $name = $file.FullName.Substring($Runtime.Length + 1).Replace('\', '/')
        $entry = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $name, [IO.Compression.CompressionLevel]::Fastest)
        $unpacked += $file.Length
    }
  } finally { $zip.Dispose() }
} else {
    $zip = [IO.Compression.ZipFile]::OpenRead($destination)
    try {
        if ($zip.Entries.Count -ne $files.Count) { throw 'Existing archive has a different payload count' }
        foreach ($entry in $zip.Entries) {
            if (-not $names.Contains($entry.FullName)) { throw 'Unexpected file in existing ZIP' }
            $unpacked += $entry.Length
        }
        if ($unpacked -ne ($files | Measure-Object -Property Length -Sum).Sum) { throw 'Existing archive has a different payload size' }
    } finally { $zip.Dispose() }
}
$stream = [IO.File]::OpenRead($destination)
$algorithm = [Security.Cryptography.SHA256]::Create()
try { $hash = [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
finally { $algorithm.Dispose(); $stream.Dispose() }
$catalog = [ordered]@{ schemaVersion = 1; packages = @([ordered]@{
    id = 'ai-cpu'; name = 'OCR + Image Caption (CPU)'; version = $Version
    url = "https://github.com/Pum2A/screensort-releases/releases/download/ai-cpu-$Version/$asset"
    sha256 = $hash; archiveBytes = (Get-Item -LiteralPath $destination).Length
    unpackedBytes = $unpacked; fileCount = $files.Count; requiredFiles = $required
}) }
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $output 'ai-packages.json'), ($catalog | ConvertTo-Json -Depth 5), $utf8)
[IO.File]::WriteAllText((Join-Path $output 'SHA256SUMS.txt'), "$hash  $asset`n", $utf8)
Copy-Item -LiteralPath (Join-Path $Runtime 'THIRD-PARTY-NOTICES.md') -Destination $output
Copy-Item -LiteralPath (Join-Path $Runtime 'FLORENCE-LICENSE.txt') -Destination $output
Write-Output ($catalog | ConvertTo-Json -Depth 5)
Write-Host "Candidate package: $destination. Review the catalog before compiling/publishing."
