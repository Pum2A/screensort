param(
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$VCRedistPath = '',
    [switch]$StageOnly,
    [switch]$SkipBackendBuild,
    [switch]$CoreOnly
)

# Build-time tools stay on the developer's PC, not on the user's machine.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$release = Join-Path $repo '.release'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stage = Join-Path $release "staging/$stamp/runtime"
$cache = Join-Path $release 'cache'
$lock = Get-Content -LiteralPath (Join-Path $repo 'packaging/windows/runtime-lock.json') -Raw | ConvertFrom-Json
$utf8 = New-Object Text.UTF8Encoding($false)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$previousSigningKey = $env:TAURI_SIGNING_PRIVATE_KEY
if (-not $StageOnly -and -not $env:TAURI_SIGNING_PRIVATE_KEY) {
    $localSigningKey = Join-Path $release 'update-signing/screensort.key'
    if (-not (Test-Path -LiteralPath $localSigningKey)) { throw 'Signed installer requires TAURI_SIGNING_PRIVATE_KEY or the protected local updater key.' }
    $env:TAURI_SIGNING_PRIVATE_KEY = $localSigningKey
}

function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed with code $LASTEXITCODE" }
}

function Hash-File([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}

function Download-Locked($Asset) {
    $destination = Join-Path $cache ([IO.Path]::GetFileName(([Uri]$Asset.url).AbsolutePath))
    if (-not (Test-Path -LiteralPath $destination)) {
        Write-Host "Downloading pinned runtime $($Asset.version)..."
        Invoke-Checked 'curl.exe' @('--fail', '--location', '--retry', '2', '--output', $destination, $Asset.url)
    }
    if ((Hash-File $destination) -ne $Asset.sha256) { throw "Checksum mismatch: $destination. Refusing to package it." }
    return $destination
}

function Expand-Safe([string]$Archive, [string]$Destination, [bool]$Postgres) {
    $root = [IO.Path]::GetFullPath($Destination) + [IO.Path]::DirectorySeparatorChar
    $zip = [IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        foreach ($entry in $zip.Entries) {
            $name = $entry.FullName.Replace('\', '/')
            if ($Postgres) {
                if ($name -notmatch '^pgsql/(bin/|lib/|share/|server_license\.txt$|commandlinetools_3rd_party_licenses\.txt$)') { continue }
                $name = $name.Substring(6)
            }
            $target = [IO.Path]::GetFullPath((Join-Path $Destination $name))
            if (-not $target.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe archive entry' }
            if ($name.EndsWith('/')) { [IO.Directory]::CreateDirectory($target) | Out-Null; continue }
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $false)
        }
    } finally { $zip.Dispose() }
}

if (-not $JdkPath -or -not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin/jlink.exe'))) {
    throw 'Pass -JdkPath pointing to a Java 25 JDK with jlink.'
}
if (Test-Path -LiteralPath $stage) { throw 'Staging directory already exists; nothing was overwritten.' }
[IO.Directory]::CreateDirectory($stage) | Out-Null
[IO.Directory]::CreateDirectory($cache) | Out-Null
Push-Location $repo
try {
    if (-not $SkipBackendBuild) {
        Invoke-Checked (Join-Path $repo 'mvnw.cmd') @('-q', '-DskipTests', 'package')
    }
    Copy-Item -LiteralPath (Join-Path $repo 'target/screensort-0.0.1-SNAPSHOT.jar') -Destination (Join-Path $stage 'backend.jar')
    Write-Host 'Creating a private Java runtime...'
    Invoke-Checked (Join-Path $JdkPath 'bin/jlink.exe') @('--add-modules', 'java.se,jdk.management,jdk.unsupported,jdk.crypto.ec,jdk.zipfs', '--strip-debug', '--no-header-files', '--no-man-pages', '--output', (Join-Path $stage 'java'))

    $python = Join-Path $stage 'python'
    if (-not $CoreOnly) {
    Write-Host 'Staging portable Python and locked model dependencies...'
    Push-Location (Join-Path $repo 'analysis-service')
    try { Invoke-Checked 'uv' @('sync', '--locked', '--extra', 'vision', '--no-python-downloads') }
    finally { Pop-Location }
    $python = Join-Path $stage 'python'
    Expand-Safe (Download-Locked $lock.python) $python $false
    $packages = Join-Path $python 'Lib/site-packages'
    [IO.Directory]::CreateDirectory($packages) | Out-Null
    $developmentPackages = Join-Path $repo 'analysis-service/.venv/Lib/site-packages'
    if (-not (Test-Path -LiteralPath $developmentPackages)) { throw 'First run uv sync --locked --extra vision in analysis-service.' }
    # No editable project link or venv hook. Copy package license metadata as well.
    foreach ($entry in Get-ChildItem -LiteralPath $developmentPackages -Force) {
        if ($entry.Name -match '^(_editable|_virtualenv|screensort_analysis.*dist-info|__pycache__|pytest|_pytest|ruff)') { continue }
        if ($entry.PSIsContainer) {
            & robocopy.exe $entry.FullName (Join-Path $packages $entry.Name) /E /XD __pycache__ /XF '*.pyc' direct_url.json /NFL /NDL /NJH /NJS /NP | Out-Null
            if ($LASTEXITCODE -ge 8) { throw "Cannot stage $($entry.Name)" }
        } else { Copy-Item -LiteralPath $entry.FullName -Destination $packages }
    }
    [IO.Directory]::CreateDirectory((Join-Path $python 'app')) | Out-Null
    & robocopy.exe (Join-Path $repo 'analysis-service/src/screensort_analysis') (Join-Path $python 'app/screensort_analysis') /E /XD __pycache__ /XF '*.pyc' /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw 'Cannot stage the analysis service' }
    [IO.File]::WriteAllText((Join-Path $python 'python314._pth'), "python314.zip`n.`nLib/site-packages`napp`nimport site`n", $utf8)
    # Existing HF cache is pinned in the recognizer. No first-start downloads.
    $models = Join-Path $repo 'analysis-service/models'
    $snapshot = Join-Path $models "models--florence-community--Florence-2-base-ft/snapshots/$($lock.model.revision)/model.safetensors"
    if (-not (Test-Path -LiteralPath $snapshot)) { throw 'The pinned Florence model must be downloaded before packaging.' }
    Copy-Item -LiteralPath (Join-Path $models 'models--florence-community--Florence-2-base-ft') -Destination (Join-Path $stage 'models/models--florence-community--Florence-2-base-ft') -Recurse
    } else {
        Write-Host 'Core-only installer: Python, ML packages and models are intentionally omitted.'
    }

    Write-Host 'Staging private PostgreSQL (without pgAdmin or StackBuilder)...'
    Expand-Safe (Download-Locked $lock.postgres) (Join-Path $stage 'postgres') $true
    # App-local CRT DLLs: never depend on a developer's system-wide VC installation.
    if (-not $VCRedistPath) {
        $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
        $visualStudio = & $vswhere -latest -products '*' -property installationPath
        if (-not $visualStudio) { throw 'Pass -VCRedistPath with the official x64 Microsoft.VC*.CRT directory.' }
        $redist = Join-Path $visualStudio 'VC/Redist/MSVC'
        $version = Get-ChildItem -LiteralPath $redist -Directory | Where-Object Name -Match '^\d+\.' | Sort-Object { [Version]$_.Name } -Descending | Select-Object -First 1
        $VCRedistPath = (Get-ChildItem -LiteralPath (Join-Path $version.FullName 'x64') -Directory -Filter 'Microsoft.VC*.CRT' | Select-Object -First 1).FullName
    }
    foreach ($binaryDirectory in @((Join-Path $stage 'java/bin'), $python, (Join-Path $stage 'postgres/bin'))) {
        if (-not (Test-Path -LiteralPath $binaryDirectory)) { continue }
        Get-ChildItem -LiteralPath $VCRedistPath -File -Filter '*.dll' | Copy-Item -Destination $binaryDirectory
    }
    if (-not $CoreOnly) {
    $requiredCrt = & (Join-Path $python 'python.exe') -I -B (Join-Path $repo 'packaging/windows/check-vc-imports.py') $stage | ConvertFrom-Json
    foreach ($dll in $requiredCrt) {
        if ($dll -eq 'msvcrt.dll' -or $dll -match '-[a-f0-9]{32}\.dll$') { continue }
        if (-not (Test-Path -LiteralPath (Join-Path $python $dll))) { throw "Missing app-local CRT dependency: $dll" }
    }
    }
    [IO.File]::WriteAllText((Join-Path $stage 'runtime-manifest.json'), ($lock | ConvertTo-Json -Depth 5), $utf8)
    Copy-Item -LiteralPath (Join-Path $repo 'packaging/windows/THIRD-PARTY-NOTICES.md') -Destination $stage
    Copy-Item -LiteralPath (Join-Path $repo 'packaging/windows/FLORENCE-LICENSE.txt') -Destination $stage
    if (-not $CoreOnly) {
    Invoke-Checked (Join-Path $python 'python.exe') @('-I', '-B', '-c', 'import torch,onnxruntime,fastapi,PIL,screensort_analysis.launch')
    Write-Host 'Portable runtime imports OK'
    $previousModelCache = $env:SCREENSORT_MODEL_CACHE
    try {
        $env:SCREENSORT_MODEL_CACHE = Join-Path $stage 'models'
        Invoke-Checked (Join-Path $python 'python.exe') @('-I', '-B', '-c', 'from screensort_analysis.image.runtime import load_analyzer;load_analyzer()')
    } finally { $env:SCREENSORT_MODEL_CACHE = $previousModelCache }
    Write-Host 'Bundled OCR and vision warm-up OK'
    }
    Invoke-Checked (Join-Path $stage 'postgres/bin/postgres.exe') @('--version')
    Invoke-Checked (Join-Path $stage 'java/bin/java.exe') @('-version')

    $destination = [IO.Path]::GetFullPath((Join-Path $repo 'frontend/src-tauri/runtime'))
    if ($destination -ne "$repo\frontend\src-tauri\runtime") { throw 'Unexpected runtime target' }
    if (Test-Path -LiteralPath $destination) {
        $backup = Join-Path $release "backups/runtime-$stamp"
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($backup)) | Out-Null
        Move-Item -LiteralPath $destination -Destination $backup
        Write-Host "Previous generated runtime preserved at $backup"
    }
    Move-Item -LiteralPath $stage -Destination $destination
    Write-Host "Runtime ready: $destination"
    if (-not $StageOnly) {
        # tauri-build copies resources but does not remove files left by an older full build.
        # Preserve the generated output before CoreOnly checks, or stale Python could mask a missing package.
        $builtRuntime = [IO.Path]::GetFullPath((Join-Path $repo 'frontend/src-tauri/target/release/runtime'))
        if ($builtRuntime -ne "$repo\frontend\src-tauri\target\release\runtime") { throw 'Unexpected build resource target' }
        if (Test-Path -LiteralPath $builtRuntime) {
            $buildBackup = Join-Path $release "backups/build-runtime-$stamp"
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($buildBackup)) | Out-Null
            Move-Item -LiteralPath $builtRuntime -Destination $buildBackup
            Write-Host "Previous generated build resources preserved at $buildBackup"
        }
        Push-Location (Join-Path $repo 'frontend')
        $checkUser = $null
        try {
            $checkSandbox = Join-Path $release "startup-check-$stamp"
            [IO.Directory]::CreateDirectory($checkSandbox) | Out-Null
            $checkData = Join-Path $checkSandbox 'data'
            $checkIdentity = @{}
            if ($env:GITHUB_ACTIONS -eq 'true' -and $env:RUNNER_ENVIRONMENT -eq 'github-hosted') {
                # Hosted runners are administrators, unlike a normal desktop user.
                # PostgreSQL deliberately refuses an elevated server process.
                # Create a disposable standard identity on the disposable VM only.
                $checkUserName = 'sscheck' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
                $checkPassword = ConvertTo-SecureString ('S!9a' + [Guid]::NewGuid().ToString('N')) -AsPlainText -Force
                $checkUser = New-LocalUser -Name $checkUserName -Password $checkPassword -AccountNeverExpires -UserMayNotChangePassword
                Add-LocalGroupMember -SID 'S-1-5-32-545' -Member $checkUser
                # This account may write only its synthetic data, not the source/runtime.
                & icacls.exe $checkSandbox /grant "*$($checkUser.SID.Value):(OI)(CI)M" /Q
                if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare the isolated standard-user smoke directory' }
                if ((Get-Service -Name seclogon).StartType -eq 'Disabled') { Set-Service -Name seclogon -StartupType Manual }
                Start-Service -Name seclogon
                $checkIdentity.Credential = New-Object Management.Automation.PSCredential ("$env:COMPUTERNAME\$checkUserName", $checkPassword)
                $checkIdentity.LoadUserProfile = $true
                # Fail before compiling Rust if this runner cannot launch the test identity.
                $identityOutput = Join-Path $checkSandbox 'identity.txt'
                $identityProbe = Start-Process -FilePath (Join-Path $env:SystemRoot 'System32/whoami.exe') -ArgumentList '/user' -WorkingDirectory $checkSandbox -WindowStyle Hidden -PassThru -Wait -RedirectStandardOutput $identityOutput @checkIdentity
                if ($identityProbe.ExitCode -ne 0 -or (Get-Content -LiteralPath $identityOutput -Raw) -notmatch [regex]::Escape($checkUser.SID.Value)) { throw 'Cannot verify the standard-user test identity' }
                Write-Host 'Standard-user startup identity verified before compilation.'
            }
            Invoke-Checked 'npm.cmd' @('run', 'tauri', '--', 'build', '--ci', '--no-bundle')
            if ($CoreOnly -and ((Test-Path -LiteralPath (Join-Path $builtRuntime 'python')) -or (Test-Path -LiteralPath (Join-Path $builtRuntime 'models')))) {
                throw 'CoreOnly build contains stale AI resources. Refusing to generate an installer.'
            }
            # Never ship solely on the basis of the manual --runtime-dir smoke path.
            # Run an identical probe beside the real binary (same resource resolver).
            # Do not execute the file that the bundler subsequently patches in-place.
            $checkExe = Join-Path $repo "frontend/src-tauri/target/release/screensort-startup-check-$stamp.exe"
            Copy-Item -LiteralPath (Join-Path $repo 'frontend/src-tauri/target/release/screensort-desktop.exe') -Destination $checkExe
            $checkArguments = @('--startup-check', '--data-dir', ('"' + $checkData + '"'))
            if ($CoreOnly) { $checkArguments += '--catalog-only' }
            $checkProcess = Start-Process -FilePath $checkExe -ArgumentList $checkArguments -WorkingDirectory ([IO.Path]::GetDirectoryName($checkExe)) -WindowStyle Hidden -PassThru -Wait -RedirectStandardOutput "$checkData.stdout.log" -RedirectStandardError "$checkData.stderr.log" @checkIdentity
            if (-not (Test-Path -LiteralPath "$checkData.report.json")) {
                throw "Tauri startup check exited with code $($checkProcess.ExitCode) without a report. No installer was generated."
            }
            $checkReport = Get-Content -LiteralPath "$checkData.report.json" -Raw | ConvertFrom-Json
            if (-not $checkReport.passed -or -not $checkReport.tauriResourceResolver) {
                # This report is produced exclusively by the isolated synthetic check.
                # Never print session logs, database passwords or process environments.
                $checkError = if ($checkReport.PSObject.Properties['error']) { [string]$checkReport.error } else { 'The real Tauri resource resolver was not verified.' }
                if ($checkError.Length -gt 2048) { $checkError = $checkError.Substring(0, 2048) }
                throw "Tauri startup check failed: $checkError No installer was generated."
            }
            if ($checkProcess.ExitCode -ne 0) { throw "Tauri startup check exited with code $($checkProcess.ExitCode). No installer was generated." }
            if ($CoreOnly -and $checkReport.bundledAiPresent) { throw 'CoreOnly startup used a full runtime. Refusing to package.' }
            Invoke-Checked 'npm.cmd' @('run', 'tauri', '--', 'bundle', '--ci', '--bundles', 'nsis')
        }
        finally {
            if ($checkUser) { Remove-LocalUser -SID $checkUser.SID }
            Pop-Location
        }
    }
} finally { Pop-Location; $env:TAURI_SIGNING_PRIVATE_KEY = $previousSigningKey }
