$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')
$root = Split-Path -Parent $PSScriptRoot

Write-Host '==> Studio build' -ForegroundColor Cyan
Push-Location (Join-Path $root 'studio')
try {
    Invoke-NativeChecked 'npm run build' { npm run build }
} finally {
    Pop-Location
}

Write-Host '==> Backend tests' -ForegroundColor Cyan
Push-Location (Join-Path $root 'backend')
try {
    Invoke-NativeChecked 'mvn test' { mvn test }
} finally {
    Pop-Location
}
