$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'backend\target\dataagent-cloud-0.1.0.jar'
$staticBundle = Join-Path $root 'backend\src\main\resources\static\studio\app.js'
$studioSource = Join-Path $root 'studio\src\SqlStudio.jsx'

$needsBuild = -not (Test-Path $jar) -or -not (Test-Path $staticBundle)
if (-not $needsBuild) {
    $needsBuild = (Get-Item $jar).LastWriteTime -lt (Get-Item $staticBundle).LastWriteTime
}
if (-not $needsBuild -and (Test-Path $studioSource)) {
    $needsBuild = (Get-Item $staticBundle).LastWriteTime -lt (Get-Item $studioSource).LastWriteTime
}

if ($needsBuild) {
    Write-Host 'Build artifacts are missing or stale. Running full build first...' -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'build.ps1')
}

Push-Location (Join-Path $root 'backend')
try {
    Write-Host 'DataAgent Cloud: http://127.0.0.1:8080/studio/' -ForegroundColor Green
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        java -jar $jar
        if ($LASTEXITCODE -ne 0) {
            throw "java -jar exited with code $LASTEXITCODE"
        }
    } finally {
        $ErrorActionPreference = $previous
    }
} finally {
    Pop-Location
}
