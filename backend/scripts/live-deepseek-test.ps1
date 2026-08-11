param(
    [Parameter(Mandatory = $true)]
    [string]$AccessToken,

    [Parameter(Mandatory = $true)]
    [string]$ProjectId,

    [string]$ApiBaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$headers = @{ Authorization = "Bearer $AccessToken" }

Write-Host "Calling Planner against the configured DeepSeek endpoint..."
$plan = Invoke-RestMethod `
    -Method Post `
    -Uri "$ApiBaseUrl/api/projects/$ProjectId/ai/planner" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body '{"instruction":"为原创青铜城调查测试章制定一个可执行计划","context":"使用项目中已确认的卡塞尔学院任务、人物知识边界和世界书上下文；不得复现原著正文。"}'

if (-not $plan.chapterTitle -or -not $plan.scenes) {
    throw "Planner response did not match the Phase 3 contract."
}

Write-Host "Planner contract passed. Calling Writer SSE..."
$writerBody = '{"instruction":"根据上下文写一个不超过三百字的原创系统测试开场","context":"路明非与楚子航准备调查青铜城入口；保持人物知识和炼金武器归属一致，不引入未给出的设定，不模仿原著文风。"}'
& curl.exe --fail-with-body --no-buffer `
    -H "Authorization: Bearer $AccessToken" `
    -H "Content-Type: application/json" `
    -d $writerBody `
    "$ApiBaseUrl/api/projects/$ProjectId/ai/writer"

if ($LASTEXITCODE -ne 0) {
    throw "Writer SSE smoke test failed with curl exit code $LASTEXITCODE."
}

Write-Host "Phase 3 live smoke test completed."
