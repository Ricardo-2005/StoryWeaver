param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "phase8-demo",
    [string]$Email = "phase8-demo@example.com",
    [string]$Password = "phase-eight-demo-password",
    [switch]$KeepExistingDemo
)

$ErrorActionPreference = "Stop"
$backendRoot = Split-Path -Parent $PSScriptRoot
$manifest = Get-Content (Join-Path $backendRoot "eval\datasets\demo-manifest.json") -Raw -Encoding UTF8 | ConvertFrom-Json

function Invoke-StoryApi {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token,
        [hashtable]$ExtraHeaders = @{}
    )
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    $arguments = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
    }
    if ($null -ne $Body) {
        $arguments.ContentType = "application/json; charset=utf-8"
        $arguments.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    Invoke-RestMethod @arguments
}

function Expand-DemoText {
    param([string]$Seed, [int]$MinimumLength)
    $builder = [System.Text.StringBuilder]::new()
    while ($builder.Length -lt $MinimumLength) { [void]$builder.Append($Seed) }
    $builder.ToString()
}

function Get-CharacterProfile {
    param([string]$Name)
    switch ($Name) {
        "路明非" { return @{ role = "卡塞尔学院学生"; location = "三峡任务现场"; abilities = "已登记的混血种基础训练"; goal = "完成青铜城调查并保存证据" } }
        "楚子航" { return @{ role = "狮心会成员"; location = "三峡任务现场"; abilities = "任务执行与已登记的言灵能力"; goal = "核对青铜城机关和炼金武器记录" } }
        "诺诺" { return @{ role = "任务支援成员"; location = "三峡任务支援点"; abilities = "现场观察与信息整理"; goal = "记录龙文机关和人物可见信息" } }
        "恺撒" { return @{ role = "学生会成员"; location = "卡塞尔学院"; abilities = "学生会任务协调"; goal = "协调青铜城行动支援" } }
        "昂热" { return @{ role = "卡塞尔学院校长"; location = "卡塞尔学院"; abilities = "任务授权与秘党资料管理"; goal = "监督调查证据与血统规则" } }
        "芬格尔" { return @{ role = "卡塞尔学院学生"; location = "卡塞尔学院"; abilities = "任务档案检索"; goal = "整理三峡行动记录" } }
        default { throw "Unknown Dragon demo character: $Name" }
    }
}

try {
    $auth = Invoke-StoryApi POST "/api/auth/register" @{
        username = $Username
        email = $Email
        password = $Password
    } $null
} catch {
    $auth = Invoke-StoryApi POST "/api/auth/login" @{
        identifier = $Username
        password = $Password
    } $null
}
$token = $auth.accessToken
if (-not $token) { throw "Authentication did not return an access token" }

$archivedProjectIds = @()
if (-not $KeepExistingDemo) {
    $existingProjects = @(Invoke-StoryApi GET "/api/projects" $null $token)
    foreach ($existing in $existingProjects) {
        $isCurrentTemplate = $existing.description -eq $manifest.description
        if ($isCurrentTemplate -and -not $existing.archived) {
            [void](Invoke-StoryApi PUT "/api/projects/$($existing.id)" @{
                name = $existing.name
                genre = $existing.genre
                description = $existing.description
                authorIntent = $existing.authorIntent
                currentFocus = $existing.currentFocus
                archived = $true
                expectedVersion = $existing.version
            } $token)
            $archivedProjectIds += $existing.id
        }
    }
}

$project = Invoke-StoryApi POST "/api/projects" @{
    name = "$($manifest.projectName) [$(Get-Date -Format 'yyyyMMdd-HHmmss')]"
    genre = $manifest.genre
    description = $manifest.description
    authorIntent = $manifest.authorIntent
    currentFocus = $manifest.currentFocus
} $token

$characters = @()
$charactersByName = @{}
for ($i = 0; $i -lt $manifest.characterNames.Count; $i++) {
    $name = $manifest.characterNames[$i]
    $profile = Get-CharacterProfile $name
    $character = Invoke-StoryApi POST "/api/projects/$($project.id)/characters" @{
        name = $name
        role = $profile.role
        description = "$name 的龙族主题技术演示人物卡；不包含原著正文。"
        personality = "仅保存本次青铜城调查所需的结构化测试特征"
        background = "与卡塞尔学院、秘党或学生组织的演示关系"
        goals = $profile.goal
        appearance = "不保存原著外貌段落"
        notes = "非商业技术演示 Fixture；章号和剧情不是原著内容"
        state = @{
            lifeStatus = "ALIVE"
            currentLocation = $profile.location
            physicalCondition = "任务开始时状态正常"
            emotionalState = "保持警觉"
            abilities = $profile.abilities
            inventoryNotes = if ($name -eq "路明非") { "完整七宗罪剑匣归属等待工作流证据确认" } else { "没有已确认的唯一炼金武器" }
            notes = "由 Dragon Template Phase 8 Seed 创建"
        }
    } $token
    $characters += $character
    $charactersByName[$name] = $character
}

$chapters = @()
for ($i = 0; $i -lt $manifest.chapterTitles.Count; $i++) {
    $chapterNo = $i + 1
    $title = $manifest.chapterTitles[$i]
    $chapter = Invoke-StoryApi POST "/api/projects/$($project.id)/chapters" @{
        chapterNo = $chapterNo
        title = "演示 $($chapterNo.ToString('00'))：$title"
        outline = "原创系统测试章 $chapterNo：推进青铜城调查，记录人物知识、炼金武器归属和事件因果。"
    } $token
    $content = Expand-DemoText "原创测试记录：调查组核对$title，所有人物行动、龙文信息和道具交接均保留证据；本段不是原著内容。" $manifest.chapterBodyCodePoints
    [void](Invoke-StoryApi POST "/api/chapters/$($chapter.id)/versions" @{
        title = "演示 $($chapterNo.ToString('00'))：$title"
        content = $content
        summary = "原创测试摘要：第 $chapterNo 个产品测试章围绕$title推进青铜城调查。"
        changeSummary = "Dragon Template deterministic demo seed"
        expectedVersion = $chapter.version
    } $token)
    $chapters += $chapter
}

for ($i = 0; $i -lt $manifest.worldbookEntryCount; $i++) {
    $fixture = $manifest.worldbookEntries[$i % $manifest.worldbookEntries.Count]
    $mode = $fixture.activationMode
    $visibilityCharacter = $fixture.visibilityCharacter
    $entry = @{
        title = "$($fixture.title) [演示索引 $(($i + 1).ToString('00'))]"
        content = "$($fixture.content) 本条是可重复世界书激活测试数据。"
        active = $true
        constantEnabled = ($mode -eq "CONSTANT")
        vectorEnabled = ($mode -eq "VECTOR")
        keywords = @($fixture.keywords) + @("青铜城调查", "fixture-$($i + 1)")
        priority = 1000 - $i
        scopeType = "PROJECT"
        visibilityType = if ($visibilityCharacter) { "CHARACTER_ONLY" } else { "ALL" }
    }
    if ($visibilityCharacter) {
        $entry.visibilityRefId = $charactersByName[$visibilityCharacter].id
    }
    [void](Invoke-StoryApi POST "/api/projects/$($project.id)/worldbook-entries" $entry $token)
}

$origin = [datetime]"2026-08-01T00:00:00Z"
for ($i = 1; $i -le $manifest.storyEventCount; $i++) {
    $chapter = $chapters[($i - 1) % $chapters.Count]
    $actor = $characters[($i - 1) % $characters.Count]
    $eventType = $i % 6
    $event = switch ($eventType) {
        0 { @{ location = "卡塞尔学院"; action = "$($actor.name)核对青铜城任务简报"; result = "任务权限和角色可见信息被记录" } }
        1 { @{ location = "三峡"; action = "$($actor.name)记录调查组抵达三峡"; result = "青铜城水下勘测可以开始" } }
        2 { @{ location = "青铜城水下入口"; action = "$($actor.name)发现入口线索"; result = "进入青铜城的前置事件被记录" } }
        3 { @{ location = "青铜城"; action = "$($actor.name)分析龙文机关"; result = "龙文信息按知情范围传播" } }
        4 { @{ location = "青铜城"; action = "$($actor.name)核对完整七宗罪剑匣登记"; result = "唯一炼金武器归属等待证据确认" } }
        default { @{ location = "卡塞尔学院"; action = "$($actor.name)提交狮心会与学生会支援记录"; result = "任务时间线完成证据复核" } }
    }
    $knownBy = @($actor.id)
    if ($actor.id -ne $characters[0].id -and $i % 3 -eq 0) { $knownBy += $characters[0].id }
    [void](Invoke-StoryApi POST "/api/projects/$($project.id)/story-events" @{
        chapterId = $chapter.id
        participantIds = @($actor.id)
        knownByIds = $knownBy
        location = $event.location
        storyTime = $origin.AddHours($i - 1).ToString("o")
        action = $event.action
        result = $event.result
        importance = [Math]::Round((0.4 + (($i % 6) * 0.1)), 1)
        evidenceParagraph = "dragon-demo-chapter-$($chapter.chapterNo)-event-$i"
    } $token)
}

$result = [ordered]@{
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    templateMarker = $manifest.templateMarker
    copyrightNotice = "角色和设定名称仅用于非商业技术演示；演示章号、剧情和文本不是原著真实章节。"
    baseUrl = $BaseUrl
    username = $Username
    projectId = $project.id
    viewpointCharacterId = $charactersByName["路明非"].id
    chapter16Id = $chapters[15].id
    archivedPreviousDemoProjectIds = $archivedProjectIds
    counts = @{
        chapters = $chapters.Count
        characters = $characters.Count
        worldbookEntries = $manifest.worldbookEntryCount
        storyEvents = $manifest.storyEventCount
    }
    uniqueItem = $manifest.uniqueItem
}
$output = Join-Path $backendRoot "target\phase8-demo-seed.json"
New-Item -ItemType Directory -Force (Split-Path -Parent $output) | Out-Null
$result | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $output

Write-Host "Dragon Template Demo created. Project: $($project.id)"
Write-Host "Archived previous marked demo projects: $($archivedProjectIds.Count)"
Write-Host "Counts: $($chapters.Count) chapters, $($characters.Count) characters, $($manifest.worldbookEntryCount) worldbook entries, $($manifest.storyEventCount) story events"
Write-Host "Seed receipt (contains no token): $output"
