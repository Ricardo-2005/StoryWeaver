param(
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$evalsDir = Split-Path -Parent $scriptDir
$reportsRoot = (Resolve-Path -LiteralPath (Join-Path $evalsDir "reports")).Path
$experimentsRoot = (Resolve-Path -LiteralPath (Join-Path $reportsRoot "experiments")).Path
$timestampPattern = '^\d{8}_\d{6}_\d{3}$'

$topLevelRuns = Get-ChildItem -LiteralPath $reportsRoot -Directory |
    Where-Object { $_.Name -match $timestampPattern }
$experimentRuns = Get-ChildItem -LiteralPath $experimentsRoot -Directory |
    Where-Object { $_.Name -match $timestampPattern } |
    Sort-Object Name -Descending
$latestCompleteExperiment = $experimentRuns | Select-Object -First 1
$latestAlias = Join-Path $experimentsRoot "latest"

if ($latestCompleteExperiment) {
    if ($WhatIf) {
        Write-Host "[WhatIf] Promote $($latestCompleteExperiment.FullName) to $latestAlias"
    } else {
        if (Test-Path -LiteralPath $latestAlias) {
            Remove-Item -LiteralPath $latestAlias -Recurse -Force
        }
        New-Item -ItemType Directory -Path $latestAlias | Out-Null
        Get-ChildItem -LiteralPath $latestCompleteExperiment.FullName -Force | ForEach-Object {
            Copy-Item -LiteralPath $_.FullName -Destination $latestAlias -Recurse
        }
        $matrixPath = Join-Path $latestAlias "matrix.json"
        if (Test-Path -LiteralPath $matrixPath) {
            $matrix = Get-Content -LiteralPath $matrixPath -Raw -Encoding UTF8 | ConvertFrom-Json
            foreach ($row in $matrix.rows) {
                $row.report = Join-Path $latestAlias $row.experiment.ToLowerInvariant()
            }
            $matrix | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $matrixPath -Encoding UTF8
        }
    }
}

$targets = @($topLevelRuns) + @($experimentRuns)

$bytes = 0L
foreach ($target in $targets) {
    $fullPath = [System.IO.Path]::GetFullPath($target.FullName)
    if (-not $fullPath.StartsWith($reportsRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to prune outside reports: $fullPath"
    }
    $bytes += (Get-ChildItem -LiteralPath $fullPath -File -Recurse | Measure-Object Length -Sum).Sum
    if ($WhatIf) {
        Write-Host "[WhatIf] Remove $fullPath"
    } else {
        Remove-Item -LiteralPath $fullPath -Recurse -Force
    }
}

Write-Host "[StoryWeaver Eval] pruned=$($targets.Count) freedMB=$([math]::Round($bytes / 1MB, 3))"
if ($latestCompleteExperiment) { Write-Host "[StoryWeaver Eval] promoted complete experiment to experiments\latest" }
