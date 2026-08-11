param([string]$Destination = (Join-Path $PSScriptRoot "..\models"))

$ErrorActionPreference = "Stop"
$expectedModelSha256 = "69a0b846f4f116b5e6aabf9546ea6754d02264f3211a13a1bd69b31b8040749a"
$modelUrl = "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/onnx/model.onnx?download=true"
$tokenizerUrl = "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/tokenizer.json?download=true"

New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$modelPath = Join-Path $Destination "model.onnx"
$tokenizerPath = Join-Path $Destination "tokenizer.json"

Invoke-WebRequest -Uri $modelUrl -OutFile $modelPath
Invoke-WebRequest -Uri $tokenizerUrl -OutFile $tokenizerPath

$actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $modelPath).Hash.ToLowerInvariant()
if ($actual -ne $expectedModelSha256) {
    throw "Downloaded ONNX model checksum mismatch."
}

Write-Host "Local ONNX embedding assets are ready in $Destination"
