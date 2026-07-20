# Ascoder 启动脚本 (Windows, Docker Compose)
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$installDir = Split-Path -Parent $scriptDir
$envFile = Join-Path $installDir ".env"

# 加载 .env
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match "^([^#][^=]+)=(.*)$") {
            $key = $Matches[1].Trim()
            $value = $Matches[2].Trim()
            Set-Item -Path "env:$key" -Value $value
        }
    }
}

$appPort = if ($env:APP_PORT) { $env:APP_PORT } else { "5173" }

Write-Host "Starting Ascoder..."
& docker compose -f (Join-Path $installDir "docker-compose.yml") up -d

# 等待后端健康
Write-Host "Waiting for backend to become healthy..."
$started = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/health" -UseBasicParsing -ErrorAction Stop
        $started = $true
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}

if ($started) {
    Write-Host "Ascoder started successfully."
    Write-Host "  Backend:  http://localhost:8080"
    Write-Host "  Frontend: http://localhost:$appPort"
} else {
    Write-Host "Warning: backend may still be starting. Check logs:"
    Write-Host "  docker compose -f `"$installDir\docker-compose.yml`" logs backend"
}
