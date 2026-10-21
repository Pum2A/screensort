param([string]$Tests = 'BatchStoreJpaTest,NamingJpaTest')
# Disposable PostgreSQL cluster under .release; never reads a user database password.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$taskRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskBin = Join-Path $taskRepo 'frontend/src-tauri/runtime/postgres/bin'
$taskArtifacts = Join-Path $taskRepo ('.release/batch-tests-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($taskArtifacts) | Out-Null
$taskData = Join-Path $taskArtifacts 'pgdata'
$taskSecretBytes = New-Object byte[] 32
$taskRng = [Security.Cryptography.RandomNumberGenerator]::Create()
$taskRng.GetBytes($taskSecretBytes)
$taskRng.Dispose()
$taskPassword = [BitConverter]::ToString($taskSecretBytes).Replace('-', '').ToLowerInvariant()
$taskPasswordFile = Join-Path $taskArtifacts 'password'
[IO.File]::WriteAllText($taskPasswordFile, $taskPassword, (New-Object Text.UTF8Encoding($false)))
$taskSocket = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$taskSocket.Start()
$taskPort = $taskSocket.LocalEndpoint.Port
$taskSocket.Stop()
$taskDatabase = 'screensort_naming_test_' + [Guid]::NewGuid().ToString('N')
$taskPreviousUrl = $env:SCREENSORT_NAMING_TEST_JDBC_URL
$taskPreviousPassword = $env:SCREENSORT_NAMING_TEST_DB_PASSWORD
$taskPreviousPgPassword = $env:PGPASSWORD
$taskStarted = $false
try {
    & (Join-Path $taskBin 'initdb.exe') -D $taskData -U naming_test --pwfile $taskPasswordFile --auth=scram-sha-256 --encoding=UTF8 --locale=C *> (Join-Path $taskArtifacts 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated initdb failed' }
    # PowerShell's native-command job waits for descendants of pg_ctl as well.
    # Wait on the pg_ctl process handle only, not the long-lived PostgreSQL child.
    $taskPgctl = Start-Process -FilePath (Join-Path $taskBin 'pg_ctl.exe') -ArgumentList @('-D', ('"' + $taskData + '"'), '-l', ('"' + (Join-Path $taskArtifacts 'postgres.log') + '"'), '-o', ('"-h 127.0.0.1 -p ' + $taskPort + '"'), '-w', 'start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskArtifacts 'pgctl.log') -RedirectStandardError (Join-Path $taskArtifacts 'pgctl-error.log')
    $null = $taskPgctl.Handle
    $taskStarted = $true
    if (-not $taskPgctl.WaitForExit(30000)) { throw 'Timed out starting isolated PostgreSQL' }
    # On Windows PowerShell 5.1 ExitCode can be null after a fast Start-Process.
    # The database protocol readiness check below is the authoritative result.
    & (Join-Path $taskBin 'pg_isready.exe') -h 127.0.0.1 -p $taskPort -U naming_test -d postgres -t 2
    if ($LASTEXITCODE -ne 0) { throw 'Isolated PostgreSQL failed to start' }
    $env:PGPASSWORD = $taskPassword
    & (Join-Path $taskBin 'createdb.exe') -h 127.0.0.1 -p $taskPort -U naming_test $taskDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create disposable test database' }
    $env:SCREENSORT_NAMING_TEST_JDBC_URL = "jdbc:postgresql://127.0.0.1:$taskPort/$taskDatabase"
    $env:SCREENSORT_NAMING_TEST_DB_PASSWORD = $taskPassword
    Push-Location $taskRepo
    try {
        # JVM warnings on stderr are not test failures (Windows PowerShell wraps them as errors).
        $ErrorActionPreference = 'Continue'
        & (Join-Path $taskRepo 'mvnw.cmd') -B test "-Dtest=$Tests" *> (Join-Path $taskArtifacts 'maven.log')
        $taskTestExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($taskTestExit -ne 0) { throw "Tests failed. See $taskArtifacts\maven.log" }
        Write-Host "Tests passed. Logs: $taskArtifacts (isolated database; user database untouched)."
    } finally { Pop-Location }
} finally {
    if ($taskStarted) { & (Join-Path $taskBin 'pg_ctl.exe') -D $taskData -w -m fast stop *> (Join-Path $taskArtifacts 'stop.log') }
    $env:SCREENSORT_NAMING_TEST_JDBC_URL = $taskPreviousUrl
    $env:SCREENSORT_NAMING_TEST_DB_PASSWORD = $taskPreviousPassword
    $env:PGPASSWORD = $taskPreviousPgPassword
}
