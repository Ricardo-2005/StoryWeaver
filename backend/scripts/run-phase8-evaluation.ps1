param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$backendRoot = Split-Path -Parent $PSScriptRoot
Push-Location $backendRoot
try {
    if ($Clean) {
        & .\mvnw.cmd clean
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    & .\mvnw.cmd "-Dtest=com.storyweaver.eval.Phase8EvaluationTest" test
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $result = Join-Path $backendRoot "target\phase8-results\phase8-results.json"
    if (-not (Test-Path $result)) {
        throw "Phase 8 result was not generated: $result"
    }
    Write-Host "Phase 8 evaluation passed. Raw result: $result"
} finally {
    Pop-Location
}
