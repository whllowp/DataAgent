$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Write-Host '==> Studio build' -ForegroundColor Cyan
Push-Location (Join-Path $root 'studio')
npm run build
Pop-Location

Write-Host '==> Backend tests' -ForegroundColor Cyan
Push-Location (Join-Path $root 'backend')
mvn test
Pop-Location
