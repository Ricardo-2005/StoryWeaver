& (Join-Path $PSScriptRoot "run-all.ps1") -Mode workflow @args
exit $LASTEXITCODE
