$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$evalsDir = Split-Path -Parent $scriptDir
$reportsRoot = Join-Path $evalsDir "reports"
$latestRoot = Join-Path $reportsRoot "latest"
$experimentsRoot = Join-Path $reportsRoot "experiments\latest"
$holdoutRoot = Join-Path $reportsRoot "holdout-v1"
$baselineRoot = Join-Path $reportsRoot "baseline-v1"

function Read-Json([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Missing report input: $Path" }
    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Add-ResultRow(
    [System.Collections.Generic.List[object]]$Rows,
    [string]$Scope,
    [string]$Configuration,
    [string]$Strategy,
    [object]$Summary,
    [object]$Rag,
    [string]$Report,
    [object]$Pool,
    [object]$FinalK
) {
    $metrics = $Summary.metrics
    $ragMetrics = $Rag.metrics
    $Rows.Add([pscustomobject][ordered]@{
        Scope = $Scope
        Dataset = $Summary.datasetVersion
        Configuration = $Configuration
        Strategy = $Strategy
        Pool = $Pool
        FinalK = $FinalK
        RagCases = $Summary.caseCounts.rag
        RecallAt1 = $metrics.ragRecallAt1
        RecallAt3 = $metrics.ragRecallAt3
        RecallAt5 = $metrics.ragRecallAt5
        RecallAt10 = $metrics.ragRecallAt10
        AllRequiredAt5 = $metrics.allRequiredHitRateAt5
        AllRequiredAt10 = $metrics.allRequiredHitRateAt10
        MRR = $metrics.mrr
        NDCGAt5 = $metrics.binaryNdcgAt5
        NDCGAt10 = $metrics.binaryNdcgAt10
        FirstRequiredMean = $metrics.meanFirstRequiredRank
        FirstRequiredMedian = $metrics.medianFirstRequiredRank
        FirstRequiredP95 = $metrics.p95FirstRequiredRank
        TokenReduction = $metrics.tokenReduction
        Preservation = $metrics.contextPreservationRate
        RagFailures = $Rag.failedCaseCount
        Consistency = $metrics.consistencyPassRate
        WorkflowStub = $metrics.workflowEngineSuccessRate
        MCP = $metrics.mcpToolSuccessRate
        Live = $(if ($Summary.live.executed) { "EXECUTED" } else { "null" })
        Report = $Report
        FailureDistribution = ($ragMetrics.failureDistribution | ConvertTo-Json -Compress)
    })
}

function Percent([object]$Value) {
    if ($null -eq $Value) { return "-" }
    return "{0:N2}%" -f (100 * [double]$Value)
}

function Number([object]$Value) {
    if ($null -eq $Value) { return "-" }
    return "{0:N4}" -f [double]$Value
}

function Plain([object]$Value) {
    if ($null -eq $Value -or "$Value" -eq "") { return "-" }
    if ("$Value" -eq "2147483647") { return "unbounded" }
    return "$Value"
}

$rows = [System.Collections.Generic.List[object]]::new()
$matrix = Read-Json (Join-Path $experimentsRoot "matrix.json")
foreach ($experiment in $matrix.rows) {
    $folder = $experiment.experiment.ToLowerInvariant()
    $runRoot = Join-Path $experimentsRoot $folder
    $summary = Read-Json (Join-Path $runRoot "summary.json")
    $rag = Read-Json (Join-Path $runRoot "rag.json")
    $scope = if ($experiment.experiment -eq "BASELINE") { "FROZEN_BASELINE_V1" } else { "V1_EXPERIMENT" }
    $report = if ($experiment.experiment -eq "BASELINE") { Join-Path $baselineRoot "summary.md" } else { Join-Path $runRoot "summary.md" }
    Add-ResultRow $rows $scope $experiment.experiment $experiment.strategy $summary $rag $report $experiment.candidatePoolSize $experiment.finalRankingSize
}

$latestSummary = Read-Json (Join-Path $latestRoot "summary.json")
$latestRag = Read-Json (Join-Path $latestRoot "rag.json")
Add-ResultRow $rows "CURRENT_FULL" "SELECTED_LATEST" $latestSummary.ragConfiguration.retrievalMode $latestSummary $latestRag (Join-Path $latestRoot "summary.md") $latestSummary.ragConfiguration.candidatePoolSize $latestSummary.ragConfiguration.finalRankingSize

$holdoutSummary = Read-Json (Join-Path $holdoutRoot "summary.json")
$holdoutRag = Read-Json (Join-Path $holdoutRoot "rag.json")
Add-ResultRow $rows "HOLDOUT_FIRST_RUN" "HOLDOUT_V1" $holdoutSummary.ragConfiguration.retrievalMode $holdoutSummary $holdoutRag (Join-Path $holdoutRoot "summary.md") $holdoutSummary.ragConfiguration.candidatePoolSize $holdoutSummary.ragConfiguration.finalRankingSize

$markdown = [System.Collections.Generic.List[string]]::new()
$markdown.Add("# StoryWeaver Evaluation - All Results")
$markdown.Add("")
$markdown.Add("- Generated: ``$((Get-Date).ToUniversalTime().ToString('o'))``")
$markdown.Add("- DeepSeek calls: ``0``; Live values remain ``null``.")
$markdown.Add("- Frozen baseline advanced ranking metrics come from the deterministic replay of the immutable v1 inputs.")
$markdown.Add("")
$markdown.Add("| Scope | Dataset | Configuration | Strategy | Pool | Final K | RAG Cases | R@1 | R@3 | R@5 | R@10 | AllReq@5 | AllReq@10 | MRR | NDCG@5 | NDCG@10 | First Req Mean | Median | P95 | Token Reduction | Preservation | RAG Failures | Consistency | Workflow Stub | MCP | Live | Failure Distribution |")
$markdown.Add("| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |")
foreach ($row in $rows) {
    $markdown.Add("| $($row.Scope) | $($row.Dataset) | $($row.Configuration) | $($row.Strategy) | $(Plain $row.Pool) | $(Plain $row.FinalK) | $(Plain $row.RagCases) | $(Percent $row.RecallAt1) | $(Percent $row.RecallAt3) | $(Percent $row.RecallAt5) | $(Percent $row.RecallAt10) | $(Percent $row.AllRequiredAt5) | $(Percent $row.AllRequiredAt10) | $(Number $row.MRR) | $(Number $row.NDCGAt5) | $(Number $row.NDCGAt10) | $(Number $row.FirstRequiredMean) | $(Number $row.FirstRequiredMedian) | $(Number $row.FirstRequiredP95) | $(Percent $row.TokenReduction) | $(Percent $row.Preservation) | $($row.RagFailures) | $(Percent $row.Consistency) | $(Percent $row.WorkflowStub) | $(Percent $row.MCP) | $($row.Live) | ``$($row.FailureDistribution)`` |")
}
$markdown.Add("")
$markdown.Add("## Report Paths")
$markdown.Add("")
foreach ($row in $rows) { $markdown.Add("- ``$($row.Configuration)``: ``$($row.Report)``") }

$markdownPath = Join-Path $latestRoot "all-results.md"
$csvPath = Join-Path $latestRoot "all-results.csv"
$htmlPath = Join-Path $latestRoot "all-results.html"
$markdown | Set-Content -LiteralPath $markdownPath -Encoding UTF8
$rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8

$consoleRows = $rows | Select-Object Scope, Configuration,
    @{Name="R@5"; Expression={ Percent $_.RecallAt5 }},
    @{Name="R@10"; Expression={ Percent $_.RecallAt10 }},
    @{Name="AllReq@10"; Expression={ Percent $_.AllRequiredAt10 }},
    @{Name="MRR"; Expression={ Number $_.MRR }},
    @{Name="Token"; Expression={ Percent $_.TokenReduction }},
    @{Name="Preserve"; Expression={ Percent $_.Preservation }},
    RagFailures,
    @{Name="Consistency"; Expression={ Percent $_.Consistency }},
    @{Name="Workflow"; Expression={ Percent $_.WorkflowStub }},
    @{Name="MCP"; Expression={ Percent $_.MCP }},
    Live

$htmlRows = $rows | Select-Object Scope, Dataset, Configuration, Strategy, Pool,
    @{Name="FinalK"; Expression={ Plain $_.FinalK }}, RagCases,
    @{Name="R@1"; Expression={ Percent $_.RecallAt1 }},
    @{Name="R@3"; Expression={ Percent $_.RecallAt3 }},
    @{Name="R@5"; Expression={ Percent $_.RecallAt5 }},
    @{Name="R@10"; Expression={ Percent $_.RecallAt10 }},
    @{Name="AllReq@5"; Expression={ Percent $_.AllRequiredAt5 }},
    @{Name="AllReq@10"; Expression={ Percent $_.AllRequiredAt10 }},
    @{Name="MRR"; Expression={ Number $_.MRR }},
    @{Name="NDCG@5"; Expression={ Number $_.NDCGAt5 }},
    @{Name="NDCG@10"; Expression={ Number $_.NDCGAt10 }},
    @{Name="FirstMean"; Expression={ Number $_.FirstRequiredMean }},
    @{Name="FirstMedian"; Expression={ Number $_.FirstRequiredMedian }},
    @{Name="FirstP95"; Expression={ Number $_.FirstRequiredP95 }},
    @{Name="TokenReduction"; Expression={ Percent $_.TokenReduction }},
    @{Name="Preservation"; Expression={ Percent $_.Preservation }},
    RagFailures,
    @{Name="Consistency"; Expression={ Percent $_.Consistency }},
    @{Name="Workflow"; Expression={ Percent $_.WorkflowStub }},
    @{Name="MCP"; Expression={ Percent $_.MCP }},
    Live, FailureDistribution
$style = @"
<style>
body{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#172033;background:#f6f8fb}
h1{margin-bottom:6px}.meta{color:#586174;margin-bottom:18px}
.table-wrap{overflow-x:auto;background:white;border:1px solid #dce1e8;border-radius:10px;box-shadow:0 2px 10px rgba(20,30,50,.06)}
table{border-collapse:collapse;white-space:nowrap;width:100%;font-size:13px}
th{position:sticky;top:0;background:#172033;color:white;text-align:left;padding:10px}
td{padding:9px 10px;border-bottom:1px solid #edf0f4}tr:nth-child(even){background:#f8fafc}tr:hover{background:#eef5ff}
</style>
"@
$htmlTable = $htmlRows | ConvertTo-Html -Fragment
$html = "<!doctype html><html><head><meta charset='utf-8'><title>StoryWeaver All Results</title>$style</head><body><h1>StoryWeaver Evaluation - All Results</h1><div class='meta'>Baseline, experiments, selected latest, holdout, Token, Consistency, Workflow Stub and MCP. DeepSeek calls: 0.</div><div class='table-wrap'>$htmlTable</div></body></html>"
$html | Set-Content -LiteralPath $htmlPath -Encoding UTF8

Write-Host ""
Write-Host "================ StoryWeaver All Results ================"
$consoleRows | Format-Table -AutoSize | Out-String -Width 4096 | Write-Host
Write-Host "Full table: $markdownPath"
Write-Host "CSV:        $csvPath"
Write-Host "HTML:       $htmlPath"
