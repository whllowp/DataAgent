param(
    [switch]$RunTests
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')
$root = Split-Path -Parent $PSScriptRoot

Write-Host '==> Redeploy build: Studio first, then backend' -ForegroundColor Cyan
Push-Location (Join-Path $root 'studio')
try {
    if (-not (Test-Path 'node_modules')) {
        Invoke-NativeChecked 'npm install' { npm install --no-audit --no-fund }
    }
    Invoke-NativeChecked 'npm run build' { npm run build }
} finally {
    Pop-Location
}

Push-Location (Join-Path $root 'backend')
try {
    if ($RunTests) {
        Invoke-NativeChecked 'mvn clean package' { mvn clean package }
    } else {
        Invoke-NativeChecked 'mvn clean package' { mvn -DskipTests clean package }
    }
} finally {
    Pop-Location
}

Write-Host ''
Write-Host 'Redeploy artifact is ready. Restart the backend process to load the new jar.' -ForegroundColor Green
Write-Host '  backend/target/dataagent-cloud-0.1.0.jar'
Write-Host '  backend/src/main/resources/static/studio/'
Write-Host '  dsh-plugin/client.js'
Write-Host ''
Write-Host 'Tip: use .\scripts\run.ps1 after stopping the old process.' -ForegroundColor DarkGray
