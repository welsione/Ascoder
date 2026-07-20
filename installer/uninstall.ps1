# Ascoder Uninstaller (Windows, Docker Compose)
param(
    [string]$InstallDir = ""
)
$ErrorActionPreference = "Stop"

if (-not $InstallDir) {
    if (Test-Path "$HOME\ascoder\docker-compose.yml") {
        $InstallDir = "$HOME\ascoder"
    } elseif (Test-Path "C:\Ascoder\docker-compose.yml") {
        $InstallDir = "C:\Ascoder"
    } else {
        Write-Host "Please specify install dir: .\uninstall.ps1 -InstallDir <path>" -ForegroundColor Red
        exit 1
    }
}

Write-Host "Ascoder Uninstall" -ForegroundColor Cyan
Write-Host ""
Write-Host "  The following will be removed:"
Write-Host "    - Install directory: $InstallDir"
Write-Host "    - Docker containers, networks, and data volumes"
Write-Host ""
Write-Host "WARNING: This cannot be undone! All data (repos, indexes) will be deleted." -ForegroundColor Red
Write-Host ""

$confirm = Read-Host "Type YES to confirm uninstall"
if ($confirm -ne "YES") {
    Write-Host "Uninstall cancelled."
    exit 0
}

# Stop and remove Docker resources
$composeFile = Join-Path $InstallDir "docker-compose.yml"
if (Test-Path $composeFile) {
    Write-Host "Stopping and removing Docker resources ..."
    & docker compose -f $composeFile down -v 2>$null
}

# Remove install directory
Write-Host "Removing directory $InstallDir ..."
Remove-Item -Path $InstallDir -Recurse -Force

Write-Host ""
Write-Host "Ascoder has been fully uninstalled."
