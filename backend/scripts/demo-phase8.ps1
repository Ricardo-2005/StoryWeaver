param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "phase8-demo",
    [string]$Password = "phase-eight-demo-password",
    [string]$SeedReceipt = "target\phase8-demo-seed.json",
    [switch]$StartLiveWorkflow
)

$ErrorActionPreference = "Stop"
$backendRoot = Split-Path -Parent $PSScriptRoot
$receiptPath = if ([IO.Path]::IsPathRooted($SeedReceipt)) { $SeedReceipt } else { Join-Path $backendRoot $SeedReceipt }
if (-not (Test-Path $receiptPath)) {
    throw "Seed receipt not found. Run scripts/seed-phase8-demo.ps1 first: $receiptPath"
}
$seed = Get-Content $receiptPath -Raw -Encoding UTF8 | ConvertFrom-Json
$manifest = Get-Content (Join-Path $backendRoot "eval\datasets\demo-manifest.json") -Raw -Encoding UTF8 | ConvertFrom-Json

function Invoke-StoryApi {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token, [hashtable]$ExtraHeaders = @{})
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    $arguments = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers }
    if ($null -ne $Body) {
        $arguments.ContentType = "application/json; charset=utf-8"
        $arguments.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    Invoke-RestMethod @arguments
}

$auth = Invoke-StoryApi POST "/api/auth/login" @{ identifier = $Username; password = $Password } $null
$token = $auth.accessToken
Write-Host "[NOTICE] Dragon character and setting names are used only for a non-commercial technical demo. Chapter numbers, plot and text are not original-novel chapters."
Write-Host "[00:00] Demo project $($seed.projectId): $($seed.counts.chapters) chapters / $($seed.counts.characters) characters / $($seed.counts.worldbookEntries) worldbook / $($seed.counts.storyEvents) events"

$characters = Invoke-StoryApi GET "/api/projects/$($seed.projectId)/characters" $null $token
$events = Invoke-StoryApi GET "/api/projects/$($seed.projectId)/story-events" $null $token
Write-Host "[00:20] State loaded: viewpoint=$($characters[0].name), events=$($events.Count)"

$preview = Invoke-StoryApi POST "/api/projects/$($seed.projectId)/worldbook/preview" @{
    query = "$($manifest.characterNames[0]) $($manifest.uniqueItem.itemName) $($manifest.currentFocus)"
    chapterId = $seed.chapter16Id
    viewpointCharacterId = $seed.viewpointCharacterId
    tokenBudget = 3000
    topK = 12
} $token
Write-Host "[00:40] Dragon worldbook preview (underwater structure / dragon-language mechanism / alchemy limits / bloodline / character visibility): selected=$($preview.selectedEntries.Count), tokens=$($preview.selectedTokens), degradedReason=$($preview.degradedReason)"

$evalResult = Join-Path $backendRoot "eval\results\phase8-results.json"
if (Test-Path $evalResult) {
    $evaluation = Get-Content $evalResult -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Host "[01:00] Fixed conflict benchmark: F1=$($evaluation.conflictEvaluation.overall.f1Percent)%, evidence=$($evaluation.conflictEvaluation.overall.evidenceLocationAccuracyPercent)%"
    Write-Host "[01:15] Context baseline: $($evaluation.contextComparison.baselineTokens) -> $($evaluation.contextComparison.storyWeaverTokens) tokens, saved $($evaluation.contextComparison.tokenSavingsPercent)%"
}

if ($StartLiveWorkflow) {
    Write-Host "[01:30] Starting live DeepSeek workflow. This consumes API quota."
    $workflow = Invoke-StoryApi POST "/api/chapters/$($seed.chapter16Id)/workflows" @{
        viewpointCharacterId = $seed.viewpointCharacterId
        instruction = $manifest.currentFocus
    } $token @{ "Idempotency-Key" = "phase8-live-$([guid]::NewGuid().ToString('N'))" }
    do {
        Start-Sleep -Milliseconds 500
        $workflow = Invoke-StoryApi GET "/api/workflows/$($workflow.id)" $null $token
        Write-Host "  step=$($workflow.status)"
    } while ($workflow.status -notin @("WAITING_APPROVAL", "BLOCKED", "FAILED", "CANCELLED", "ROLLED_BACK"))
    Write-Host "[02:20] Workflow=$($workflow.status), reviewIssues=$($workflow.reviewIssues.Count), tokenEstimate=$($workflow.contextPacket.tokenEstimate)"
    Write-Host "Approval is intentionally manual; the demo script never promotes facts or changes canon automatically."
} else {
    Write-Host "[01:30] Live workflow skipped. Add -StartLiveWorkflow to consume the configured DeepSeek quota."
}

$costs = Invoke-StoryApi GET "/api/projects/$($seed.projectId)/costs" $null $token
Write-Host "[02:35] Usage: requests=$($costs.requests), actualCost=$($costs.actualCost), unpriced=$($costs.unpricedRequests)"

$mcpBody = @{
    jsonrpc = "2.0"
    id = 8
    method = "tools/call"
    params = @{
        name = "get_item_owner"
        arguments = @{ projectId = $seed.projectId; itemKey = $seed.uniqueItem.itemKey }
    }
} | ConvertTo-Json -Depth 10 -Compress
$mcp = Invoke-RestMethod -Method POST -Uri "$BaseUrl/mcp" -Headers @{
    Authorization = "Bearer $token"
    Accept = "application/json, text/event-stream"
    "MCP-Protocol-Version" = "2025-11-25"
} -ContentType "application/json" -Body $mcpBody
Write-Host "[02:50] MCP get_item_owner completed; isError=$($mcp.result.isError)"
Write-Host "[03:00] Demo complete. Grafana shows workflow duration, LLM usage, cost, review and trace panels."
