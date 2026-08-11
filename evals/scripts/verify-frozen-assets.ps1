$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$evalsDir = Split-Path -Parent $scriptDir
$repoRoot = Split-Path -Parent $evalsDir

$assets = @(
    @{ Path = "evals\datasets\rag\retrieval_cases.jsonl"; Hash = "CF5969C299F21F437CEBF28A4E3087175E672EB41635239C41521D11E60ADF21" },
    @{ Path = "evals\fixtures\worldbook\eval-project-v1.json"; Hash = "37E856BD348BC1E4FB5113669D350E74EA80630C180A8237C953EB49991B9DF7" },
    @{ Path = "evals\reports\baseline-v1\summary.md"; Hash = "1D71CA2EE3362297800A6F38B24FDDDCB8C0235F520D5F8539FAF0421792EDE2" },
    @{ Path = "evals\reports\baseline-v1\summary.json"; Hash = "0DEF8250BF155989CF5955320FEA11553E56B5478613AEDA4635E7F17F7CD128" },
    @{ Path = "evals\reports\baseline-v1\raw\failures.json"; Hash = "785216F8E54849EF807410B8A363C205EFA213F030B6F661890658269B91C883" },
    @{ Path = "evals\datasets\rag-holdout-v1\retrieval_cases.jsonl"; Hash = "E3FDB2F167E4C9BB609C2E08DCE23599017258EF8BF5CEED0FF15A85248AD8A6" },
    @{ Path = "evals\fixtures\worldbook\eval-holdout-v1.json"; Hash = "2299697101EE76CB8334D929055B3E6CC98DD609055E455DA6FF7DFB5E86CE39" }
)

foreach ($asset in $assets) {
    $fullPath = Join-Path $repoRoot $asset.Path
    if (-not (Test-Path -LiteralPath $fullPath)) { throw "Frozen asset is missing: $($asset.Path)" }
    $actual = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash
    if ($actual -ne $asset.Hash) {
        throw "Frozen asset hash mismatch: $($asset.Path); expected=$($asset.Hash); actual=$actual"
    }
}

Write-Host "[StoryWeaver Eval] frozen baseline and holdout hashes verified"
