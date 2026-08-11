param(
    [ValidateSet("all", "rag", "token", "rag-token", "consistency", "workflow", "mcp", "live")]
    [string]$Mode = "all",

    [ValidateSet("local", "ci", "live")]
    [string]$Profile = "local",

    [string]$DatasetVersion = "v1",

    [string]$Output = "",

    [ValidateRange(1, 20)]
    [int]$Repetitions = 1,

    [ValidateSet("BASELINE", "CONSTANT_ISOLATED", "KEYWORD_ONLY", "VECTOR_ONLY", "HYBRID_FUSION")]
    [string]$RagStrategy = "VECTOR_ONLY",

    [ValidateRange(0, 1000)]
    [int]$RagCandidatePool = 0,

    [ValidateRange(0, 2147483647)]
    [int]$RagFinalK = 0,

    [ValidateRange(1, 1000)]
    [int]$RagRrfK = 60
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$evalsDir = Split-Path -Parent $scriptDir
$repoRoot = Split-Path -Parent $evalsDir
$maven = Join-Path $repoRoot "backend\mvnw.cmd"
$pom = Join-Path $evalsDir "pom.xml"
$frozenVerifier = Join-Path $scriptDir "verify-frozen-assets.ps1"

if ($Mode -eq "live") {
    if ($env:STORYWEAVER_EVAL_LIVE -ine "true") {
        Write-Error "Live evaluation is disabled. Set STORYWEAVER_EVAL_LIVE=true explicitly."
        exit 2
    }
    $Profile = "live"
}

if (-not (Test-Path -LiteralPath $maven)) {
    Write-Error "Missing Maven wrapper: $maven"
    exit 3
}

& $frozenVerifier

Push-Location $repoRoot
try {
    $unboundedTen = $RagStrategy -in @("BASELINE", "VECTOR_ONLY")
    if ($RagCandidatePool -eq 0) { $RagCandidatePool = if ($unboundedTen) { 10 } else { 30 } }
    if ($RagFinalK -eq 0) { $RagFinalK = if ($unboundedTen) { 2147483647 } else { 10 } }
    Write-Host "[StoryWeaver Eval] mode=$Mode profile=$Profile dataset=$DatasetVersion rag=$RagStrategy pool=$RagCandidatePool finalK=$RagFinalK repetitions=$Repetitions"
    & $maven -q -f $pom "-Deval.mode=$Mode" "-Deval.profile=$Profile" "-Deval.datasetVersion=$DatasetVersion" "-Deval.output=$Output" "-Deval.repetitions=$Repetitions" "-Deval.ragStrategy=$RagStrategy" "-Deval.ragCandidatePool=$RagCandidatePool" "-Deval.ragFinalK=$RagFinalK" "-Deval.ragRrfK=$RagRrfK" test exec:java
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) { exit $exitCode }
    Write-Host "[StoryWeaver Eval] latest report: $evalsDir\reports\latest\summary.md"
    exit 0
}
finally {
    Pop-Location
}
