$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'backend\target\dataagent-cloud-0.1.0.jar'
if (-not (Test-Path $jar)) {
    throw "JAR not found: $jar. Run scripts/build.ps1 first."
}

$port = if ($env:SCHEDULER_TEST_PORT) { [int]$env:SCHEDULER_TEST_PORT } else { 18080 }
$base = "http://127.0.0.1:$port"
$token = 'semantic-scheduler-test'
$old = @{}
$names = @('SERVER_PORT','METADATA_DB_URL','METADATA_DB_USER','METADATA_DB_PASSWORD','DATAAGENT_API_TOKEN','DATAAGENT_DEMO_ENABLED','DATAAGENT_SEMANTIC_SYNC_ENABLED','DATAAGENT_SEMANTIC_SYNC_INITIAL_DELAY_MS','DATAAGENT_SEMANTIC_SYNC_INTERVAL_MS')
foreach ($name in $names) { $old[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$env:SERVER_PORT = "$port"
$env:METADATA_DB_URL = 'jdbc:h2:mem:dataagent_scheduler_verify;MODE=PostgreSQL;DB_CLOSE_DELAY=-1'
$env:METADATA_DB_USER = 'sa'
$env:METADATA_DB_PASSWORD = ''
$env:DATAAGENT_API_TOKEN = $token
$env:DATAAGENT_DEMO_ENABLED = 'false'
$env:DATAAGENT_SEMANTIC_SYNC_ENABLED = 'true'
$env:DATAAGENT_SEMANTIC_SYNC_INITIAL_DELAY_MS = '1000'
$env:DATAAGENT_SEMANTIC_SYNC_INTERVAL_MS = '1500'

$process = $null
try {
    Write-Host "Starting temporary backend on $base with 1s/1.5s scheduler..." -ForegroundColor Cyan
    $process = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar) -WorkingDirectory (Join-Path $root 'backend') -PassThru -WindowStyle Hidden

    $healthy = $false
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $health = Invoke-RestMethod "$base/api/health" -TimeoutSec 2
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (-not $healthy) { throw 'Temporary backend did not become healthy.' }

    $headers = @{ 'X-API-Key' = $token }
    $scheduled = $null
    for ($i = 0; $i -lt 30; $i++) {
        $runs = Invoke-RestMethod "$base/api/semantic/sync/runs" -Headers $headers
        $scheduled = @($runs) | Where-Object { $_.triggerType -eq 'SCHEDULED' } | Select-Object -First 1
        if ($scheduled) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $scheduled) { throw 'No SCHEDULED sync run appeared within 15s.' }

    Write-Host 'Scheduler verification passed:' -ForegroundColor Green
    $scheduled | Select-Object triggerType,status,scannedAssets,scannedKnowledge,createdLinks,updatedLinks,removedLinks,startedAt | Format-List
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
    }
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $old[$name], 'Process')
    }
}
