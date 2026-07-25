<#
.SYNOPSIS
Ascoder 局域网服务器自动更新脚本（Windows PowerShell 版，由计划任务定时调用）。

.DESCRIPTION
设计约束：服务器在局域网内，只能主动从 GitHub 拉取，GitHub 无法反向访问服务器。
因此部署完全基于"拉取"：拉最新 compose 文件 + 拉最新镜像 + 重启容器。

用法（通常由计划任务触发，也可手动执行）：
  powershell -ExecutionPolicy Bypass -File scripts\server\deploy.ps1

日志：写入同目录上级的 deploy.log。
#>

$ErrorActionPreference = 'Stop'

# 部署根目录 = 脚本所在目录上三级（scripts\server\ -> 仓库根）
$DeployDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $DeployDir

$ComposeFile = 'docker-compose.prod.yml'
$LogFile = Join-Path $DeployDir 'deploy.log'

function Write-Log {
    param([string]$Message)
    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    $line = "[$timestamp] $Message"
    Write-Host $line
    Add-Content -Path $LogFile -Value $line
}

# 1. 拉取最新的 compose 与脚本（源码已打进镜像，仓库在这里仅用于同步部署文件）
# 容错策略：服务器到 GitHub 经代理，网络易抖动，fetch 重试 3 次。
# fetch 或 checkout 任一失败即中止部署：镜像走 GHCR CDN 通常能拉到新版，若此时用旧 compose
# 启动新镜像，会造成配置不一致（如 volume 挂载缺失、env 变化），比"暂不更新"更危险。
Write-Log 'fetching latest deployment files from origin/master...'
$fetchOk = $false
for ($attempt = 1; $attempt -le 3; $attempt++) {
    & git fetch origin master --depth=1 --force --quiet 2>>$LogFile
    if ($LASTEXITCODE -eq 0) {
        $fetchOk = $true
        break
    }
    if ($attempt -lt 3) { Write-Log "WARN: git fetch attempt $attempt failed, retrying in 2s..." }
    Start-Sleep -Seconds 2
}
if (-not $fetchOk) {
    Write-Log 'ERROR: git fetch failed after 3 attempts. Aborting to avoid config mismatch (new image with stale compose).'
    exit 1
}

# 只 checkout 部署相关文件，不触碰 .env / data/（它们未跟踪，见 .gitignore）
& git checkout origin/master -- $ComposeFile .env.example scripts/ 2>>$LogFile
if ($LASTEXITCODE -ne 0) {
    Write-Log "ERROR: git checkout failed (exit code $LASTEXITCODE). Aborting."
    exit 1
}
Write-Log 'deployment files synced.'

# 1.5 自动生成 ASCODER_ENCRYPTION_KEY（首次部署）
# 非开发环境必须配置加密密钥，未配置会导致配置 LLM provider 时 encrypt() 抛异常 -> 409。
function Get-RandomBase64Key {
    $openssl = Get-Command openssl -ErrorAction SilentlyContinue
    if ($openssl) {
        $key = & openssl rand -base64 32 2>$null
        if ($LASTEXITCODE -eq 0 -and $key) { return $key.Trim() }
    }
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}

$envFile = Join-Path $DeployDir '.env'
if (Test-Path $envFile) {
    $content = Get-Content $envFile -Raw
    if ($content -notmatch '(?m)^ASCODER_ENCRYPTION_KEY=.') {
        $key = Get-RandomBase64Key
        if ($content -match '(?m)^ASCODER_ENCRYPTION_KEY=') {
            $content = $content -replace '(?m)^ASCODER_ENCRYPTION_KEY=.*', "ASCODER_ENCRYPTION_KEY=$key"
            Set-Content -Path $envFile -Value $content -NoNewline
        } else {
            Add-Content -Path $envFile -Value "ASCODER_ENCRYPTION_KEY=$key"
        }
        Write-Log 'generated ASCODER_ENCRYPTION_KEY into .env (auto)'
    }
}

# 2. 拉取最新镜像
Write-Log 'pulling images...'
$pullOutput = & docker compose -f $ComposeFile pull 2>&1
$pullOutput | ForEach-Object { Write-Log $_ }
if ($LASTEXITCODE -ne 0) {
    Write-Log 'ERROR: image pull failed (check GHCR login if repo is private)'
    exit 1
}

# 3. 重启（镜像未变时 compose 不会重启容器，避免无谓抖动）
Write-Log 'recreating containers if image changed...'
$upOutput = & docker compose -f $ComposeFile up -d 2>&1
$upOutput | ForEach-Object { Write-Log $_ }
if ($LASTEXITCODE -ne 0) {
    Write-Log "ERROR: docker compose up failed (exit code $LASTEXITCODE)"
    exit 1
}

# 4. 清理悬挂的旧镜像
& docker image prune -f 2>&1 | Out-Null

Write-Log 'update done.'
