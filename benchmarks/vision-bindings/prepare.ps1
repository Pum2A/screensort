$ErrorActionPreference = 'Stop'
function File-Sha256([string] $path) {
    $stream = [IO.File]::OpenRead($path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $algorithm.Dispose() }
}
$runtimeDirectory = Join-Path $PSScriptRoot '.runtime'
New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null

# Public artifacts only. No user images are uploaded or modified.
$modelPath = 'validated/vision/classification/mobilenet/model/mobilenetv2-12.onnx'
# Pinned from the official model's Git LFS pointer, verified on 2026-10-11.
$expectedHash = 'c0c3f76d93fa3fd6580652a45618618a220fced18babf65774ed169de0432ad5'
$modelFile = Join-Path $runtimeDirectory 'mobilenetv2-12.onnx'
if (-not (Test-Path -LiteralPath $modelFile)) {
    Invoke-WebRequest -UseBasicParsing "https://media.githubusercontent.com/media/onnx/models/main/$modelPath" -OutFile $modelFile
}
if ((File-Sha256 $modelFile) -ne $expectedHash) {
    throw 'Model checksum mismatch.'
}

$jarFile = Join-Path $runtimeDirectory 'onnxruntime-1.26.0.jar'
$jarUrl = 'https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.26.0/onnxruntime-1.26.0.jar'
if (-not (Test-Path -LiteralPath $jarFile)) {
    Invoke-WebRequest -UseBasicParsing $jarUrl -OutFile $jarFile
}
# Recorded after verifying the first download against Maven Central's checksum.
$jarHash = 'cf5a48c6f5d07b15f10634b80433ddce8f5892662b1a122bbbc0907f4f442c60'
if ((File-Sha256 $jarFile) -ne $jarHash) {
    throw 'Java artifact checksum mismatch.'
}

# All three bindings use the exact same native engine DLL from installed Python.
$pythonCapi = (& python -c "import pathlib,onnxruntime; print(pathlib.Path(onnxruntime.__file__).parent/'capi')").Trim()
Copy-Item -LiteralPath (Join-Path $pythonCapi 'onnxruntime.dll') -Destination $runtimeDirectory
Copy-Item -LiteralPath (Join-Path $pythonCapi 'onnxruntime_providers_shared.dll') -Destination $runtimeDirectory
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($jarFile)
try {
    $entry = $archive.GetEntry('ai/onnxruntime/native/win-x64/onnxruntime4j_jni.dll')
    if (-not $entry) { throw 'Windows x64 JNI library missing.' }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $runtimeDirectory 'onnxruntime4j_jni.dll'), $true)
} finally { $archive.Dispose() }
Write-Output "Model SHA256: $(File-Sha256 $modelFile)"
Write-Output "JAR SHA256: $(File-Sha256 $jarFile)"
Write-Output "Engine SHA256: $(File-Sha256 (Join-Path $runtimeDirectory 'onnxruntime.dll'))"
