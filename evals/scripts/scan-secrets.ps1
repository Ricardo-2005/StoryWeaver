$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$evalsDir = Split-Path -Parent $scriptDir
$repoRoot = Split-Path -Parent $evalsDir

$extensions = @(".java", ".kt", ".ts", ".js", ".vue", ".json", ".yml", ".yaml", ".md", ".ps1", ".cmd", ".properties", ".env", ".example")
$excludedSegments = @("\target\", "\node_modules\", "\reports\", "\.cache\", "\models\", "\dist\", "\coverage\")
$patterns = @(
    [regex]::new("sk-[A-Za-z0-9_-]{20,}"),
    [regex]::new("(?i)Bearer\s+[A-Za-z0-9._-]{20,}"),
    [regex]::new("(?i)(DEEPSEEK_API_KEY|OPENAI_API_KEY|JWT_SECRET)\s*[:=]\s*[`"']?[A-Za-z0-9_./+-]{24,}")
)

$findings = New-Object System.Collections.Generic.List[string]
Get-ChildItem -LiteralPath $repoRoot -Recurse -File -Force | Where-Object {
    $candidatePath = $_.FullName
    $extensions -contains $_.Extension.ToLowerInvariant() -and
    -not ($excludedSegments | Where-Object { $candidatePath.Contains($_) })
} | ForEach-Object {
    $path = $_.FullName
    if ($_.Name -eq ".env") { return }
    try {
        $content = [System.IO.File]::ReadAllText($path)
        foreach ($pattern in $patterns) {
            foreach ($match in $pattern.Matches($content)) {
                $normalized = $match.Value.ToLowerInvariant()
                $placeholder = $normalized -match "placeholder|changeme|change[_-]?this|please[_-]?change|replace|your[_-]|example|dummy|test[_-]|phase[0-9]|local[_-]|development|\$\{"
                if (-not $placeholder) {
                    $relative = $path.Substring($repoRoot.Length).TrimStart('\')
                    $findings.Add($relative)
                    break
                }
            }
            if ($findings.Contains($path.Substring($repoRoot.Length).TrimStart('\'))) { break }
        }
    } catch {
        throw "Secret scan could not read: $path"
    }
}

$unique = $findings | Sort-Object -Unique
if ($unique.Count -gt 0) {
    Write-Error "Potential secrets detected in $($unique.Count) file(s); values are intentionally not printed: $($unique -join ', ')"
    exit 1
}
Write-Host "[StoryWeaver Eval] secret scan passed: 0 potential secrets"
