$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Write-Host '==> Building Studio + DSH client bundle' -ForegroundColor Cyan
Push-Location (Join-Path $root 'studio')
if (-not (Test-Path 'node_modules')) {
    npm install --no-audit --no-fund
}
npm run build
Pop-Location

Write-Host '==> Building Spring Boot jar' -ForegroundColor Cyan
Push-Location (Join-Path $root 'backend')
mvn -DskipTests clean package
Pop-Location

Write-Host ''
Write-Host 'Build complete:' -ForegroundColor Green
Write-Host '  backend/target/dataagent-cloud-0.1.0.jar'
Write-Host '  backend/src/main/resources/static/studio/'
Write-Host '  dsh-plugin/client.js'
