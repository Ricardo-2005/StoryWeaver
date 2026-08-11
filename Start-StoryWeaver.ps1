[CmdletBinding()]
param(
    [switch]$NoBrowser,
    [switch]$Rebuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$backendDirectory = Join-Path $projectRoot 'backend'
$frontendDirectory = Join-Path $projectRoot 'frontend'
$backendEnvFile = Join-Path $backendDirectory '.env'
$backendEnvExample = Join-Path $backendDirectory '.env.example'
$frontendUrl = 'http://127.0.0.1:4173'
$frontendHealthUrl = "$frontendUrl/healthz"

function Write-Stage {
    param([Parameter(Mandatory)][string]$Message)
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Assert-Command {
    param([Parameter(Mandatory)][string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "'$Name' was not found. Install Docker Desktop and ensure its CLI is on PATH."
    }
}

function Get-ConfiguredPort {
    param(
        [Parameter(Mandatory)][string]$EnvFile,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][int]$Default
    )

    $portLine = Get-Content -LiteralPath $EnvFile | Where-Object {
        $_ -match "^\s*$([regex]::Escape($Name))\s*=\s*\d+\s*$"
    } | Select-Object -First 1

    if ($null -eq $portLine) {
        return $Default
    }

    $port = [int](($portLine -split '=', 2)[1].Trim())
    if ($port -lt 1 -or $port -gt 65535) {
        throw "$Name in $EnvFile must be between 1 and 65535."
    }

    return $port
}

function Wait-ForHttpEndpoint {
    param(
        [Parameter(Mandatory)][string]$Url,
        [Parameter(Mandatory)][string]$Name,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                Write-Host "$Name is ready: $Url" -ForegroundColor Green
                return
            }
        }
        catch {
            # The service is still initializing.
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)

    throw "$Name did not pass its health check within $TimeoutSeconds seconds: $Url"
}

function Start-ComposeStack {
    param(
        [string[]]$ComposeFileArguments = @(),
        [Parameter(Mandatory)][string]$FailureMessage
    )

    $arguments = @()
    $arguments += $ComposeFileArguments
    $arguments += 'up', '-d'
    if ($Rebuild) {
        $arguments += '--build'
    }

    & docker compose @arguments
    if ($LASTEXITCODE -ne 0) {
        throw $FailureMessage
    }
}

try {
    Write-Stage 'Checking prerequisites'
    Assert-Command -Name 'docker'
    & docker compose version | Out-Host
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Engine is unavailable. Start Docker Desktop, wait until it is running, then retry.'
    }

    if (-not (Test-Path $backendEnvFile)) {
        if (-not (Test-Path $backendEnvExample)) {
            throw "Backend configuration template is missing: $backendEnvExample"
        }
        Copy-Item -LiteralPath $backendEnvExample -Destination $backendEnvFile
        Write-Host "Created local configuration: $backendEnvFile" -ForegroundColor Yellow
        Write-Host 'Set DEEPSEEK_API_KEY in that file and restart the service to enable AI writing.' -ForegroundColor Yellow
    }

    $backendPort = Get-ConfiguredPort -EnvFile $backendEnvFile -Name 'APP_PORT' -Default 8080
    $prometheusPort = Get-ConfiguredPort -EnvFile $backendEnvFile -Name 'PROMETHEUS_PORT' -Default 9090
    $grafanaPort = Get-ConfiguredPort -EnvFile $backendEnvFile -Name 'GRAFANA_PORT' -Default 3000
    $backendHealthUrl = "http://127.0.0.1:$backendPort/actuator/health"

    $modelPath = Join-Path $backendDirectory 'models\model.onnx'
    $tokenizerPath = Join-Path $backendDirectory 'models\tokenizer.json'
    if (-not ((Test-Path $modelPath) -and (Test-Path $tokenizerPath))) {
        Write-Host 'Embedding model files were not found; the application will use keyword retrieval.' -ForegroundColor Yellow
        Write-Host 'Run backend\scripts\download-embedding-model.ps1 to enable semantic retrieval.' -ForegroundColor Yellow
    }

    $startupMode = if ($Rebuild) { 'rebuilding images' } else { 'using existing images' }
    Write-Stage "Starting backend services ($startupMode)"
    Push-Location $backendDirectory
    try {
        Start-ComposeStack -ComposeFileArguments @() -FailureMessage 'Backend Compose startup failed.'
    }
    finally {
        Pop-Location
    }

    Write-Stage 'Waiting for backend health check'
    try {
        Wait-ForHttpEndpoint -Url $backendHealthUrl -Name 'Backend'
    }
    catch {
        Push-Location $backendDirectory
        try { & docker compose logs --tail=120 app | Out-Host } finally { Pop-Location }
        throw
    }

    Write-Stage "Starting frontend ($startupMode)"
    Push-Location $frontendDirectory
    $previousBackendUpstream = $env:BACKEND_UPSTREAM
    $env:BACKEND_UPSTREAM = "http://host.docker.internal:$backendPort"
    try {
        Start-ComposeStack -ComposeFileArguments @('-f', 'compose.frontend.yaml') -FailureMessage 'Frontend Compose startup failed.'
    }
    finally {
        if ($null -eq $previousBackendUpstream) {
            Remove-Item Env:BACKEND_UPSTREAM -ErrorAction SilentlyContinue
        }
        else {
            $env:BACKEND_UPSTREAM = $previousBackendUpstream
        }
        Pop-Location
    }

    Write-Stage 'Waiting for frontend health check'
    try {
        Wait-ForHttpEndpoint -Url $frontendHealthUrl -Name 'Frontend'
    }
    catch {
        Push-Location $frontendDirectory
        try { & docker compose -f compose.frontend.yaml logs --tail=120 frontend | Out-Host } finally { Pop-Location }
        throw
    }

    Write-Host "`nStoryWeaver is running: $frontendUrl" -ForegroundColor Green
    Write-Host "Observability: Grafana http://127.0.0.1:$grafanaPort, Prometheus http://127.0.0.1:$prometheusPort"
    if (-not $NoBrowser) {
        Start-Process $frontendUrl
    }
}
catch {
    Write-Host "`nStartup failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'See docs\GETTING_STARTED.md for troubleshooting.' -ForegroundColor Yellow
    exit 1
}
