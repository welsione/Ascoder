# Ascoder 停止脚本 (Windows, Docker Compose)
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$installDir = Split-Path -Parent $scriptDir

Write-Host "Stopping Ascoder..."
& docker compose -f (Join-Path $installDir "docker-compose.yml") down
Write-Host "Ascoder stopped."
