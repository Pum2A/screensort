# Explicit public write: only the reviewed distribution README, never a source tree.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskReadmeEncoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $repo 'packaging/distribution/README.md')))
& gh api --method PUT repos/Pum2A/screensort-releases/contents/README.md -f 'message=docs: explain distribution-only preview releases' -f "content=$taskReadmeEncoded" --jq .content.html_url
if ($LASTEXITCODE -ne 0) { throw 'Cannot initialize distribution README; nothing else was published' }
