$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'backend\target\dataagent-cloud-0.1.0.jar'
if (-not (Test-Path $jar)) {
    throw "JAR not found: $jar. Run scripts/build.ps1 first."
}

$port = if ($env:BENCHMARK_TEST_PORT) { [int]$env:BENCHMARK_TEST_PORT } else { 18083 }
$base = "http://127.0.0.1:$port"
if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $port is already in use; set BENCHMARK_TEST_PORT to a free port."
}
$token = 'sre-benchmark-scheduler-test'
$names = @('SERVER_PORT','METADATA_DB_URL','METADATA_DB_USER','METADATA_DB_PASSWORD','DATAAGENT_API_TOKEN','DATAAGENT_DEMO_ENABLED','DATAAGENT_SEMANTIC_SYNC_ENABLED','DATAAGENT_BENCHMARK_SYNC_ENABLED','DATAAGENT_BENCHMARK_SYNC_INITIAL_DELAY_MS','DATAAGENT_BENCHMARK_SYNC_INTERVAL_MS')
$old = @{}
foreach ($name in $names) { $old[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

$env:SERVER_PORT = "$port"
$env:METADATA_DB_URL = 'jdbc:h2:mem:dataagent_benchmark_scheduler;MODE=PostgreSQL;DB_CLOSE_DELAY=-1'
$env:METADATA_DB_USER = 'sa'
$env:METADATA_DB_PASSWORD = ''
$env:DATAAGENT_API_TOKEN = $token
$env:DATAAGENT_DEMO_ENABLED = 'true'
$env:DATAAGENT_SEMANTIC_SYNC_ENABLED = 'false'
$env:DATAAGENT_BENCHMARK_SYNC_ENABLED = 'true'
$env:DATAAGENT_BENCHMARK_SYNC_INITIAL_DELAY_MS = '1000'
$env:DATAAGENT_BENCHMARK_SYNC_INTERVAL_MS = '1500'

$process = $null
try {
    Write-Host "Starting temporary backend on $base with 1s/1.5s benchmark scheduler..." -ForegroundColor Cyan
    $process = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar) -WorkingDirectory (Join-Path $root 'backend') -PassThru -WindowStyle Hidden

    $healthy = $false
    for ($i = 0; $i -lt 80; $i++) {
        try {
            $health = Invoke-RestMethod "$base/api/health" -TimeoutSec 2
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (-not $healthy) { throw 'Temporary backend did not become healthy.' }

    $headers = @{ 'X-API-Key' = $token }
    $completed = $null
    for ($i = 0; $i -lt 40; $i++) {
        $items = Invoke-RestMethod "$base/api/benchmarks" -Headers $headers
        $completed = @($items) | Where-Object { $_.lastRunAt -ne $null }
        if ($completed.Count -ge 3) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $completed -or $completed.Count -lt 3) {
        throw "Only $($completed.Count) benchmark cases ran within 20s."
    }

    Write-Host 'SRE benchmark scheduler verification passed:' -ForegroundColor Green
    $completed | Select-Object name,lastStatus,lastDurationMs,lastRunAt | Format-Table -AutoSize
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
    }
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $old[$name], 'Process')
    }
}
