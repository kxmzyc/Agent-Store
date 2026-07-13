[CmdletBinding()]
param(
  [switch]$Build,
  [switch]$Open,
  [string]$ProjectName = 'agent-store-defense',
  [int]$AgentHostPort = 8001
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Invoke-Docker {
  param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
  $previousPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    & docker @Arguments
    $dockerExitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousPreference
  }
  if ($dockerExitCode -ne 0) {
    throw "docker $($Arguments -join ' ') failed with exit code $dockerExitCode"
  }
}

function Test-DockerEngine {
  $previousPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'SilentlyContinue'
    & docker version *> $null
    $dockerExitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousPreference
  }
  return $dockerExitCode -eq 0
}

if (-not (Test-Path -LiteralPath '.env')) {
  throw 'Missing .env. Create it from .env.example before the defense.'
}

if (-not (Test-DockerEngine)) {
  Write-Host '[WAIT] Starting Docker Desktop...'
  Invoke-Docker desktop start

  $engineReady = $false
  for ($attempt = 1; $attempt -le 30; $attempt++) {
    Start-Sleep -Seconds 3
    if (Test-DockerEngine) {
      $engineReady = $true
      break
    }
  }
  if (-not $engineReady) {
    throw 'Docker Desktop did not become ready within 90 seconds.'
  }
}

$env:AGENT_HOST_PORT = [string]$AgentHostPort
$upArgs = @('compose', '-p', $ProjectName, 'up', '-d')
if ($Build) {
  $upArgs += '--build'
}

Write-Host "[WAIT] Starting project '$ProjectName' (Agent host port $AgentHostPort)..."
Invoke-Docker @upArgs

$expectedServices = @('mysql', 'backend', 'agent-service', 'frontend')
$servicesReady = $false
$rows = @()
for ($attempt = 1; $attempt -le 60; $attempt++) {
  $jsonLines = @(& docker compose -p $ProjectName ps --format json 2>$null)
  $rows = @($jsonLines | ForEach-Object { $_ | ConvertFrom-Json })
  $readyServices = @($rows | Where-Object {
      $_.Service -in $expectedServices -and $_.State -eq 'running' -and $_.Health -eq 'healthy'
    } | Select-Object -ExpandProperty Service -Unique)
  if ($readyServices.Count -eq $expectedServices.Count) {
    $servicesReady = $true
    break
  }
  Start-Sleep -Seconds 3
}

if (-not $servicesReady) {
  & docker compose -p $ProjectName ps --all
  throw 'The four defense services did not become healthy within 180 seconds.'
}

$frontendStatus = (Invoke-WebRequest 'http://127.0.0.1/' -UseBasicParsing -TimeoutSec 10).StatusCode
$backendHealth = Invoke-RestMethod 'http://127.0.0.1:8081/health' -TimeoutSec 10
$productPage = Invoke-RestMethod 'http://127.0.0.1:8081/api/products?page=1&size=1' -TimeoutSec 10
$agentHealthJson = & docker compose -p $ProjectName exec -T agent-service python -c "import urllib.request; print(urllib.request.urlopen('http://localhost:8000/health', timeout=5).read().decode())"
if ($LASTEXITCODE -ne 0) {
  throw 'Agent health check failed inside the container.'
}
$agentHealth = $agentHealthJson | ConvertFrom-Json

Write-Host ''
Write-Host '[PASS] 4/4 containers are healthy.' -ForegroundColor Green
Write-Host "[PASS] Frontend HTTP status: $frontendStatus" -ForegroundColor Green
Write-Host "[PASS] Backend status: $($backendHealth.status)" -ForegroundColor Green
Write-Host "[PASS] Agent status: $($agentHealth.status); LLM enabled: $($agentHealth.llmEnabled); tools: $(@($agentHealth.tools).Count)" -ForegroundColor Green
Write-Host "[PASS] Product API total: $($productPage.total)" -ForegroundColor Green
Write-Host "[INFO] Frontend: http://127.0.0.1/"
Write-Host "[INFO] Backend Swagger: http://127.0.0.1:8081/swagger-ui/index.html"
Write-Host "[INFO] Agent direct port: http://127.0.0.1:$AgentHostPort"
Write-Host '[INFO] Demo users: alice / 123456, admin / 123456'

if ($Open) {
  Start-Process 'http://127.0.0.1/'
}
