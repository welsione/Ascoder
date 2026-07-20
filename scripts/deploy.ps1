param(
    [switch]$BuildOnly
)

$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $rootDir

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Created .env from .env.example."
    Write-Host "Please edit .env and set at least one LLM API key, then run this script again."
    exit 1
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Error "Docker is not installed or not available on PATH."
}

$composeArgs = @("compose")
try {
    & docker @composeArgs "version" *> $null
} catch {
    $legacyCompose = Get-Command docker-compose -ErrorAction SilentlyContinue
    if (-not $legacyCompose) {
        Write-Error "Docker Compose is not installed."
    }
    $composeArgs = @()
}

if ($composeArgs.Count -gt 0) {
    if ($BuildOnly) {
        & docker @composeArgs "build"
    } else {
        & docker @composeArgs "up" "-d" "--build"
    }
} else {
    if ($BuildOnly) {
        & docker-compose "build"
    } else {
        & docker-compose "up" "-d" "--build"
    }
}

$appPort = "5173"
Get-Content ".env" | ForEach-Object {
    if ($_ -match "^APP_PORT=(.+)$") {
        $script:appPort = $Matches[1].Trim()
    }
}

Write-Host "Ascoder is starting."
Write-Host "Open: http://localhost:$appPort"
if ($composeArgs.Count -gt 0) {
    Write-Host "Logs: docker compose logs -f backend frontend"
} else {
    Write-Host "Logs: docker-compose logs -f backend frontend"
}
