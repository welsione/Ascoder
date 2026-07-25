<#
.SYNOPSIS
Ascoder 局域网部署一次性安装脚本（Windows PowerShell 版）。

.DESCRIPTION
在 Windows 服务器上执行，做四件事：
  1. 检查依赖（git / docker / docker compose）
  2. 浅克隆仓库到 ASCODER_HOME（默认 C:\ascoder），仅用于同步 compose 与脚本
  3. 从 .env.example 生成 .env（如不存在），自动生成加密密钥
  4. 注册 Windows 计划任务，定时调用 deploy.ps1 拉取更新

.PARAMETER InstallDir
部署目录，默认 C:\ascoder。可用环境变量 ASCODER_HOME 覆盖。

.PARAMETER RepoUrl
仓库地址，默认 https://github.com/welsione/Ascoder.git。

.PARAMETER Branch
跟踪分支，默认 master。

.PARAMETER IntervalMin
计划任务拉取间隔（分钟），默认 5。

.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts\server\install.ps1

.EXAMPLE
$env:ASCODER_HOME = 'D:\ascoder'; powershell -ExecutionPolicy Bypass -File scripts\server\install.ps1
#>

param(
    [string]$InstallDir = $(if ($env:ASCODER_HOME) { $env:ASCODER_HOME } else { 'C:\ascoder' }),
    [string]$RepoUrl = $(if ($env:ASCODER_REPO) { $env:ASCODER_REPO } else { 'https://github.com/welsione/Ascoder.git' }),
    [string]$Branch = $(if ($env:ASCODER_BRANCH) { $env:ASCODER_BRANCH } else { 'master' }),
    [int]$IntervalMin = $(if ($env:CRON_INTERVAL_MIN) { [int]$env:CRON_INTERVAL_MIN } else { 5 })
)

$ErrorActionPreference = 'Stop'
$TaskName = 'AscoderAutoDeploy'

Write-Host '=== Ascoder LAN Deploy Installer (Windows) ==='
Write-Host "Install dir : $InstallDir"
Write-Host "Repo        : $RepoUrl"
Write-Host "Branch      : $Branch"
Write-Host "Task        : every $IntervalMin min"
Write-Host ''

# 1. 依赖检查
function Test-Command {
    param([string]$Name)
    $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

if (-not (Test-Command 'git')) { throw 'git not found, install Git for Windows first (https://git-scm.com/download/win)' }
if (-not (Test-Command 'docker')) { throw 'docker not found, install Docker Desktop first (https://www.docker.com/products/docker-desktop)' }
$composeVersion = & docker compose version 2>&1
if ($LASTEXITCODE -ne 0) { throw "docker compose plugin not found: $composeVersion" }
if (-not (Test-Command 'openssl')) {
    Write-Host 'WARN: openssl not found, will use .NET to generate encryption key if needed'
}

# 2. 克隆或更新仓库（带重试，应对到 GitHub 网络不稳定）
function Sync-Repo {
    if (Test-Path (Join-Path $InstallDir '.git')) {
        Write-Host "existing repo found at $InstallDir, syncing..."
        Push-Location $InstallDir
        try {
            & git fetch origin $Branch --depth=1 --quiet 2>$null
            if ($LASTEXITCODE -eq 0) {
                & git checkout -B $Branch "origin/$Branch" --quiet
            } else {
                Write-Host 'WARN: fetch failed (network unstable), keeping existing checkout.'
            }
        } finally {
            Pop-Location
        }
        return $true
    }

    $parent = Split-Path $InstallDir -Parent
    if ($parent -and -not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }

    Write-Host 'cloning repo (shallow, with retries)...'
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        & git clone --depth=1 --branch $Branch $RepoUrl $InstallDir 2>$null
        if ($LASTEXITCODE -eq 0) { return $true }
        Write-Host "  clone attempt $attempt failed, retrying in 3s..."
        if (Test-Path $InstallDir) { Remove-Item $InstallDir -Recurse -Force }
        Start-Sleep -Seconds 3
    }
    return $false
}

$cloned = Sync-Repo
if (-not $cloned) { throw 'failed to clone/sync repo after retries (check network to github.com)' }

Set-Location $InstallDir

# 3. 生成 .env（首次安装）
$envFile = Join-Path $InstallDir '.env'
$envExample = Join-Path $InstallDir '.env.example'
if (-not (Test-Path $envFile)) {
    if (Test-Path $envExample) {
        Copy-Item $envExample $envFile
        Write-Host 'created .env from template.'
    } else {
        Write-Host 'WARN: .env.example not found, skipping .env creation.'
    }
} else {
    Write-Host '.env already exists, keep it.'
}

# 3.5 自动生成 ASCODER_ENCRYPTION_KEY（未配置时）
# 非开发环境必须配置加密密钥（ApiKeyEncryptor 在非 dev profile 下不允许默认密钥）。
function Get-RandomBase64Key {
    if (Test-Command 'openssl') {
        $key = & openssl rand -base64 32 2>$null
        if ($LASTEXITCODE -eq 0 -and $key) { return $key.Trim() }
    }
    # .NET 兜底：生成 32 字节随机数并 Base64 编码
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}

function Ensure-EncryptionKey {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    $content = Get-Content $Path -Raw
    if ($content -match '(?m)^ASCODER_ENCRYPTION_KEY=.') { return }
    $key = Get-RandomBase64Key
    if ($content -match '(?m)^ASCODER_ENCRYPTION_KEY=') {
        $content = $content -replace '(?m)^ASCODER_ENCRYPTION_KEY=.*', "ASCODER_ENCRYPTION_KEY=$key"
        Set-Content -Path $Path -Value $content -NoNewline
    } else {
        Add-Content -Path $Path -Value "ASCODER_ENCRYPTION_KEY=$key"
    }
    Write-Host 'generated ASCODER_ENCRYPTION_KEY into .env (auto)'
}

Ensure-EncryptionKey $envFile

# 3.6 预创建数据目录
# docker compose 挂载的 ./data/* 默认 root 拥有，容器内 ascoder 用户无写权限。
# 这里仅预创建目录；属主由容器 entrypoint 启动时 chown 修正（见 backend/docker-entrypoint.sh）。
$dataDirs = @('data\repos', 'data\worktrees', 'data\project-spaces', 'data\codegraph')
foreach ($d in $dataDirs) {
    $fullPath = Join-Path $InstallDir $d
    if (-not (Test-Path $fullPath)) { New-Item -ItemType Directory -Path $fullPath -Force | Out-Null }
}

# 4. 注册 / 更新计划任务
$deployScript = Join-Path $InstallDir 'scripts\server\deploy.ps1'
if (-not (Test-Path $deployScript)) {
    Write-Host "WARN: deploy.ps1 not found at $deployScript, skipping task registration."
} else {
    # Register-ScheduledTask 的 -Once -RepetitionInterval 在 PS 5.1 需配合 -RepetitionDuration。
    # 9999 天约 27 年，Windows Task Scheduler 上限即此值，视为无限期。
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' `
        -Argument "-ExecutionPolicy Bypass -NoProfile -File `"$deployScript`""
    $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
        -RepetitionInterval (New-TimeSpan -Minutes $IntervalMin) `
        -RepetitionDuration (New-TimeSpan -Days 9999)
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopOnIdleEnd

    # 以当前用户身份注册（Interactive：需用户登录才触发；SYSTEM 账户可无人值守但 Docker Desktop 需服务模式）
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive
    Register-ScheduledTask -TaskName $TaskName `
        -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
    Write-Host "scheduled task registered: $TaskName (every $IntervalMin min)"
}

# 5. 后续指引
Write-Host ''
Write-Host '=== Install complete ==='
Write-Host ''
Write-Host 'Next steps:'
Write-Host "  1. Edit config:  notepad $InstallDir\.env"
Write-Host '       - set MYSQL_PASSWORD (must match your host MySQL user)'
Write-Host '       - set LLM keys, or use the database provider in Settings UI'
Write-Host '  2. Ensure MySQL 8 is running on the host at port 3306'
Write-Host '       (backend connects via host.docker.internal:3306)'
Write-Host '  3. (Only if repo is private) login to GHCR:'
Write-Host '       echo "<PAT-with-read:packages>" | docker login ghcr.io -u <github-user> --password-stdin'
Write-Host "  4. Start now:  powershell -ExecutionPolicy Bypass -File `"$deployScript`""
Write-Host "       or wait for scheduled task to fire within $IntervalMin min."
Write-Host ''
Write-Host "Logs: $InstallDir\deploy.log"
Write-Host "Docs: $InstallDir\DEPLOY-LAN.md"
