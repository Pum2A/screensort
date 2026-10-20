param([switch]$UploadToGitHub)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$key = Join-Path $repo '.release/update-signing/screensort.key'
if (-not (Test-Path -LiteralPath $key)) { throw 'Generate the signing key first; never overwrite an existing production key.' }
# Private material must not inherit permissions from a broadly shared checkout.
$security = New-Object System.Security.AccessControl.FileSecurity
$security.SetAccessRuleProtection($true, $false)
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$rule = New-Object System.Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'Allow')
$security.AddAccessRule($rule)
[IO.File]::SetAccessControl($key, $security)
if ($UploadToGitHub) {
    # stdin, never an argument, console output, source file or public release asset.
    Get-Content -LiteralPath $key -Raw | & gh secret set TAURI_SIGNING_PRIVATE_KEY --repo Pum2A/screensort
    if ($LASTEXITCODE -ne 0) { throw 'Cannot set the signing secret; no key was printed.' }
}
Write-Host 'Signing key protected. Keep an encrypted offline backup; losing it prevents trusted updates.'
