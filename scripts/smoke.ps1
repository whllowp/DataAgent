$ErrorActionPreference = 'Stop'
$base = if ($env:DATAAGENT_URL) { $env:DATAAGENT_URL.TrimEnd('/') } else { 'http://127.0.0.1:8080' }
$token = if ($env:DATAAGENT_API_TOKEN) { $env:DATAAGENT_API_TOKEN } else { 'dataagent-dev-token' }
$headers = @{ 'X-API-Key' = $token }

Write-Host "==> Health: $base" -ForegroundColor Cyan
$health = Invoke-RestMethod "$base/api/health" -Headers $headers
$health | ConvertTo-Json -Depth 4

Write-Host '==> Datasources' -ForegroundColor Cyan
$datasources = Invoke-RestMethod "$base/api/datasources" -Headers $headers
if (-not $datasources.items -or $datasources.items.Count -eq 0) {
    throw 'No datasource. Start the backend with DATAAGENT_DEMO_ENABLED=true or register one in Studio.'
}
$ds = $datasources.items[0]
Write-Host ("Using {0} ({1}, {2})" -f $ds.name, $ds.dialect, $ds.id)

Write-Host '==> Execute SELECT' -ForegroundColor Cyan
$body = @{
    datasourceId = $ds.id
    sql = 'SELECT * FROM customers LIMIT 3'
    maxRows = 3
    problem = 'smoke test'
    tags = 'smoke'
} | ConvertTo-Json
$query = Invoke-RestMethod "$base/api/sql/execute" -Method Post -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
[pscustomobject]@{
    queryId = $query.queryId
    status = $query.status
    rows = $query.rowCount
    elapsedMs = $query.elapsedMs
    columns = ($query.columns | ForEach-Object { $_.name }) -join ', '
} | Format-List

Write-Host '==> Explain' -ForegroundColor Cyan
$explainBody = @{
    datasourceId = $ds.id
    sql = 'SELECT * FROM customers LIMIT 3'
} | ConvertTo-Json
$explain = Invoke-RestMethod "$base/api/sql/explain" -Method Post -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($explainBody))
Write-Host ("format={0}, summary={1}" -f $explain.plan.format, $explain.plan.summary)

Write-Host '==> Optimize analyze' -ForegroundColor Cyan
$analyzeBody = @{
    datasourceId = $ds.id
    sql = "SELECT * FROM customers WHERE LOWER(name) LIKE '%客户-01%' ORDER BY created_at"
    includePlan = $true
} | ConvertTo-Json
$analysis = Invoke-RestMethod "$base/api/optimize/analyze" -Method Post -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($analyzeBody))
Write-Host ("findings={0}, prompt={1} chars" -f $analysis.findings.Count, $analysis.promptContext.Length)

Write-Host ''
Write-Host 'Smoke test passed.' -ForegroundColor Green
