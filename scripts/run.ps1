$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'backend\target\dataagent-cloud-0.1.0.jar'

if (-not (Test-Path $jar)) {
    Write-Host 'Jar not found. Running build first...' -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'build.ps1')
}

Push-Location (Join-Path $root 'backend')
try {
    Write-Host 'DataAgent Cloud: http://127.0.0.1:8080/studio/' -ForegroundColor Green
    java -jar $jar
} finally {
    Pop-Location
}
