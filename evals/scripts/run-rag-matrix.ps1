param(
    [string]$DatasetVersion = "v1"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$evalsDir = Split-Path -Parent $scriptDir
$repoRoot = Split-Path -Parent $evalsDir
$stamp = Get-Date -Format "yyyyMMdd_HHmmss_fff"
$matrixRoot = Join-Path $evalsDir "reports\experiments\$stamp"
New-Item -ItemType Directory -Path $matrixRoot -Force | Out-Null

$strategies = @(
    @{ Label = "BASELINE"; Name = "BASELINE"; Pool = 10; FinalK = 2147483647 },
    @{ Label = "CONSTANT_ISOLATED"; Name = "CONSTANT_ISOLATED"; Pool = 10; FinalK = 2147483647 },
    @{ Label = "KEYWORD_ONLY"; Name = "KEYWORD_ONLY"; Pool = 10; FinalK = 2147483647 },
    @{ Label = "VECTOR_ONLY"; Name = "VECTOR_ONLY"; Pool = 10; FinalK = 2147483647 },
    @{ Label = "HYBRID_FUSION"; Name = "HYBRID_FUSION"; Pool = 10; FinalK = 2147483647 },
    @{ Label = "HYBRID_POOL_30"; Name = "HYBRID_FUSION"; Pool = 30; FinalK = 2147483647 },
    @{ Label = "HYBRID_POOL_30_FINAL_10"; Name = "HYBRID_FUSION"; Pool = 30; FinalK = 10 }
)

$rows = @()
foreach ($strategy in $strategies) {
    $runDir = Join-Path $matrixRoot $strategy.Label.ToLowerInvariant()
    & (Join-Path $scriptDir "run-all.ps1") -Mode all -Profile local -DatasetVersion $DatasetVersion -Output $runDir -RagStrategy $strategy.Name -RagCandidatePool $strategy.Pool -RagFinalK $strategy.FinalK -RagRrfK 60
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $summary = Get-Content (Join-Path $runDir "summary.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    $rag = Get-Content (Join-Path $runDir "rag.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    $rows += [ordered]@{
        experiment = $strategy.Label
        strategy = $strategy.Name
        candidatePoolSize = $strategy.Pool
        finalRankingSize = $strategy.FinalK
        recallAt1 = $summary.metrics.ragRecallAt1
        recallAt3 = $summary.metrics.ragRecallAt3
        recallAt5 = $summary.metrics.ragRecallAt5
        recallAt10 = $summary.metrics.ragRecallAt10
        requiredHitAt5 = $summary.metrics.requiredHitRateAt5
        requiredHitAt10 = $summary.metrics.requiredHitRateAt10
        allRequiredHitAt5 = $summary.metrics.allRequiredHitRateAt5
        allRequiredHitAt10 = $summary.metrics.allRequiredHitRateAt10
        mrr = $summary.metrics.mrr
        binaryNdcgAt5 = $summary.metrics.binaryNdcgAt5
        binaryNdcgAt10 = $summary.metrics.binaryNdcgAt10
        tokenReduction = $summary.metrics.tokenReduction
        contextPreservationRate = $summary.metrics.contextPreservationRate
        failedCases = $rag.failedCaseCount
        failureDistribution = $rag.metrics.failureDistribution
        report = $runDir
    }
}

$matrix = [ordered]@{
    datasetVersion = $DatasetVersion
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    evaluationType = "DETERMINISTIC"
    embeddingModel = "BAAI/bge-small-zh-v1.5"
    vectorSearch = "EXACT_COSINE"
    deepSeekCalls = 0
    rows = $rows
}
$jsonPath = Join-Path $matrixRoot "matrix.json"
$mdPath = Join-Path $matrixRoot "matrix.md"
$matrix | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$lines = @(
    "# StoryWeaver RAG Experiment Matrix",
    "",
    "- Dataset: ``$DatasetVersion``",
    "- DeepSeek calls: ``0``",
    "- Candidate generation: production ``WorldbookService.previewWithOptions``",
    "",
    "| Strategy | R@5 | R@10 | AllReq@5 | AllReq@10 | MRR | NDCG@5 | NDCG@10 | Token reduction | Preservation | Failures |",
    "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
)
foreach ($row in $rows) {
    $lines += "| $($row.experiment) | $([math]::Round(100*$row.recallAt5,2))% | $([math]::Round(100*$row.recallAt10,2))% | $([math]::Round(100*$row.allRequiredHitAt5,2))% | $([math]::Round(100*$row.allRequiredHitAt10,2))% | $([math]::Round($row.mrr,4)) | $([math]::Round($row.binaryNdcgAt5,4)) | $([math]::Round($row.binaryNdcgAt10,4)) | $([math]::Round(100*$row.tokenReduction,2))% | $([math]::Round(100*$row.contextPreservationRate,2))% | $($row.failedCases) |"
}
$lines | Set-Content -LiteralPath $mdPath -Encoding UTF8

$latestRoot = Join-Path $evalsDir "reports\experiments\latest"
if (Test-Path -LiteralPath $latestRoot) { Remove-Item -LiteralPath $latestRoot -Recurse -Force }
New-Item -ItemType Directory -Path $latestRoot -Force | Out-Null
Get-ChildItem -LiteralPath $matrixRoot -Force | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $latestRoot -Recurse
}
$latestMatrixPath = Join-Path $latestRoot "matrix.json"
$latestMatrix = Get-Content -LiteralPath $latestMatrixPath -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($row in $latestMatrix.rows) {
    $row.report = Join-Path $latestRoot $row.experiment.ToLowerInvariant()
}
$latestMatrix | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $latestMatrixPath -Encoding UTF8
Write-Host "[StoryWeaver Eval] experiment matrix: $mdPath"
