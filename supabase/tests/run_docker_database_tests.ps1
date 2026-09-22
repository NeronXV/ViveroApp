#requires -Version 7.0
param([switch]$KeepRunning)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$dockerPath = (Get-Command docker -ErrorAction Stop).Source
$context = & $dockerPath context inspect desktop-linux | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $context.Endpoints.docker.Host -ne 'npipe:////./pipe/dockerDesktopLinuxEngine') {
    throw 'This runner requires the local Docker Desktop Linux engine.'
}

$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$container = "vivero-validation-db-$runId"
$network = "vivero-validation-$runId"
$logDirectory = Join-Path $projectRoot "tmp\database-validation-$runId"
New-Item -ItemType Directory -Path $logDirectory | Out-Null
$testPassword = [guid]::NewGuid().ToString('N')
$images = @{
    postgres = 'public.ecr.aws/supabase/postgres:17.6.1.158'
    auth = 'public.ecr.aws/supabase/gotrue:v2.196.0'
    storage = 'public.ecr.aws/supabase/storage-api:v1.71.0'
    pgtap = 'public.ecr.aws/supabase/pg_prove:3.36'
}

function Invoke-TestDocker {
    param([string]$Stage, [string[]]$Arguments)
    $output = & $dockerPath --context desktop-linux @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $safeOutput = ($output -join "`n").Replace($testPassword, '[REDACTED]')
    [System.IO.File]::WriteAllText((Join-Path $logDirectory "$Stage.log"), $safeOutput)
    if ($exitCode -ne 0) {
        $safeOutput -split "`n" | Select-Object -Last 30 | Write-Output
        throw "Stage $Stage failed (exit $exitCode). See $logDirectory"
    }
    if ($Stage -eq 'pgtap') { Write-Output $safeOutput }
    Write-Output "PASS: $Stage"
}

foreach ($imageName in $images.Keys) {
    Invoke-TestDocker "image-$imageName" @('image', 'inspect', $images[$imageName], '--format', '{{.Id}}')
}

$containerCreated = $false
try {
    Invoke-TestDocker 'network' @('network', 'create', '--internal', '--label', 'purpose=vivero-release-validation', $network)
    Invoke-TestDocker 'database' @(
        'run', '-d', '--pull=never', '--name', $container, '--network', $network,
        '--label', 'purpose=vivero-release-validation',
        '-e', 'POSTGRES_USER=supabase_admin', '-e', "POSTGRES_PASSWORD=$testPassword",
        '--mount', "type=bind,source=$projectRoot\supabase,target=/workspace/supabase,readonly",
        $images.postgres
    )
    $containerCreated = $true
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        # Wait for the final TCP server, not the temporary initialization server.
        $null = & $dockerPath --context desktop-linux exec $container pg_isready -h 127.0.0.1 -U supabase_admin -d postgres 2>&1
        $ready = $LASTEXITCODE -eq 0
        if ($ready) { break }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    if (-not $ready) { throw 'PostgreSQL did not become ready in 60 seconds.' }

    Invoke-TestDocker 'service-roles' @(
        'exec', $container, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', 'supabase_admin', '-d', 'postgres',
        '-c', "alter role supabase_auth_admin password '$testPassword'; alter role supabase_storage_admin password '$testPassword';"
    )
    Invoke-TestDocker 'auth-migrations' @(
        'run', '--rm', '--pull=never', '--network', $network,
        '-e', 'GOTRUE_DB_DRIVER=postgres',
        '-e', "GOTRUE_DB_DATABASE_URL=postgresql://supabase_auth_admin:${testPassword}@${container}:5432/postgres",
        '-e', 'GOTRUE_SITE_URL=http://localhost', '-e', 'API_EXTERNAL_URL=http://localhost',
        '-e', "GOTRUE_JWT_SECRET=$testPassword", $images.auth, 'auth', 'migrate'
    )
    Invoke-TestDocker 'storage-migrations' @(
        'run', '--rm', '--pull=never', '--network', $network,
        '-e', "DATABASE_URL=postgresql://supabase_storage_admin:${testPassword}@${container}:5432/postgres",
        '-e', "AUTH_JWT_SECRET=$testPassword", '-e', 'STORAGE_BACKEND=file',
        '-e', 'FILE_STORAGE_BACKEND_PATH=/tmp/storage', '-e', 'REGION=local',
        '-e', 'GLOBAL_S3_BUCKET=local', '-e', 'TENANT_ID=local',
        '--entrypoint', 'node', $images.storage, 'dist/scripts/migrate-call.js'
    )
    Invoke-TestDocker 'application-migrations' @(
        'exec', $container, 'sh', '-c',
        'set -e; for f in /workspace/supabase/migrations/*.sql; do echo "MIGRATION: $f"; psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres -f "$f"; done'
    )
    Invoke-TestDocker 'pgtap' @(
        'run', '--rm', '--pull=never', '--network', $network, '-e', "PGPASSWORD=$testPassword",
        '--mount', "type=bind,source=$projectRoot\supabase\tests\database,target=/tests,readonly",
        $images.pgtap, 'pg_prove', '--host', $container, '--port', '5432',
        '--username', 'postgres', '--dbname', 'postgres', '--ext', '.sql', '--recurse', '/tests'
    )
    & (Join-Path $PSScriptRoot 'concurrency\cashier.ps1') -Container $container |
        Tee-Object -FilePath (Join-Path $logDirectory 'concurrency.log')

    $files = @(Get-ChildItem (Join-Path $projectRoot 'supabase\migrations') -Filter '*.sql') +
        @(Get-ChildItem (Join-Path $PSScriptRoot 'database') -Filter '*.sql') +
        @(Get-ChildItem (Join-Path $PSScriptRoot 'concurrency') -File) +
        @(Get-Item $PSCommandPath)
    $hashes = foreach ($file in $files) {
        [pscustomobject]@{
            path = $file.FullName.Substring($projectRoot.Length + 1)
            sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        }
    }
    [pscustomobject]@{
        completedUtc = [DateTime]::UtcNow.ToString('o')
        container = $container
        images = $images
        result = 'PASS'
        files = $hashes
    } | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $logDirectory 'result.json')
    Write-Output "PASS: fresh database, pgTAP and concurrent payments. Evidence: $logDirectory"
} finally {
    if ($containerCreated -and -not $KeepRunning) {
        $null = & $dockerPath --context desktop-linux stop $container
        if ($LASTEXITCODE -ne 0) { Write-Warning "Could not stop $container" }
    }
    # Keep the test database and logs for inspection. Never remove existing volumes.
    Write-Output "Validation container: $container; network: $network"
}
