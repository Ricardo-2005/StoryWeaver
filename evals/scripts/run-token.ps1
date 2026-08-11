& (Join-Path $PSScriptRoot "run-all.ps1") -Mode token @args
exit $LASTEXITCODE
