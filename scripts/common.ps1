function Invoke-NativeChecked {
    param(
        [Parameter(Mandatory = $true)][string]$Description,
        [Parameter(Mandatory = $true)][scriptblock]$Command
    )

    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Command
        if ($LASTEXITCODE -ne 0) {
            throw "$Description failed with exit code $LASTEXITCODE"
        }
    } finally {
        $ErrorActionPreference = $previous
    }
}
