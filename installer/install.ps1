# Ascoder Installer (Windows, Docker Compose)
# Run from PowerShell
param()
$ErrorActionPreference = "Stop"

$VERSION = "0.1.0"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Blue }
function Write-Ok($msg) { Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err($msg) { Write-Host "[ERROR] $msg" -ForegroundColor Red }

function Read-Prompt($msg, $default = "") {
    if ($default) {
        Write-Host "${msg} [$default]: " -NoNewline -ForegroundColor Yellow
    } else {
        Write-Host "${msg}: " -NoNewline -ForegroundColor Yellow
    }
    $inputVal = Read-Host
    if ($inputVal -and $inputVal.Trim()) {
        return $inputVal.Trim()
    }
    return $default
}

function Confirm-YesNo($msg) {
    Write-Host "${msg} [Y/n]: " -NoNewline -ForegroundColor Yellow
    $answer = Read-Host
    return ($answer -notmatch "^n|^N")
}

# ============================================================
# [1/6] Welcome
# ============================================================
Write-Host ""
Write-Host "Ascoder v$VERSION - Team Code Understanding Platform"
Write-Host ""
Write-Host "This wizard will guide you through:"
Write-Host "  1. Choose install directory"
Write-Host "  2. Detect Docker environment"
Write-Host "  3. Configure MySQL connection"
Write-Host "  4. Configure LLM API"
Write-Host "  5. Generate config and start"
Write-Host ""

if (-not (Confirm-YesNo "Start installation?")) {
    Write-Host "Installation cancelled."
    exit 0
}

# ============================================================
# [2/6] Install Directory
# ============================================================
$defaultInstallDir = "$HOME\ascoder"
$installDir = Read-Prompt "Install directory" $defaultInstallDir

if (Test-Path $installDir) {
    $envFile = Join-Path $installDir ".env"
    if (Test-Path $envFile) {
        Write-Warn "Directory $installDir already exists and contains .env."
        if (-not (Confirm-YesNo "Overwrite?")) {
            Write-Host "Installation cancelled."
            exit 0
        }
    }
} else {
    New-Item -ItemType Directory -Path $installDir -Force | Out-Null
}

# Copy orchestration files
Write-Info "Copying orchestration files to $installDir ..."
Copy-Item -Path "$scriptDir\docker-compose.yml" -Destination $installDir -Force -ErrorAction SilentlyContinue
Copy-Item -Path "$scriptDir\docker-compose.ssh.yml" -Destination $installDir -Force -ErrorAction SilentlyContinue
Copy-Item -Path "$scriptDir\docker-compose.host-repos.yml" -Destination $installDir -Force -ErrorAction SilentlyContinue
Copy-Item -Path "$scriptDir\.env.example" -Destination "$installDir\.env.example" -Force -ErrorAction SilentlyContinue
if (Test-Path "$scriptDir\installer\bin") {
    Copy-Item -Path "$scriptDir\installer\bin" -Destination $installDir -Recurse -Force -ErrorAction SilentlyContinue
}
if (Test-Path "$scriptDir\backend") {
    Copy-Item -Path "$scriptDir\backend" -Destination $installDir -Recurse -Force -ErrorAction SilentlyContinue
}
if (Test-Path "$scriptDir\frontend") {
    Copy-Item -Path "$scriptDir\frontend" -Destination $installDir -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Ok "Orchestration files copied."

# ============================================================
# [3/6] Docker Environment Detection
# ============================================================
Write-Host ""
Write-Info "Detecting Docker environment ..."

$dockerExe = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerExe) {
    Write-Err "Docker not found. Please install Docker Desktop:"
    Write-Err "  https://docs.docker.com/desktop/install/windows-install/"
    Write-Err "  Ensure WSL2 backend is enabled (Settings -> Use WSL2 based engine)"
    Read-Host "Press Enter after installing Docker..."
    $dockerExe = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerExe) {
        Write-Err "Docker still not available. Installation aborted."
        exit 1
    }
}

try {
    $dockerInfo = & docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Docker daemon is not running. Please start Docker Desktop."
        Read-Host "Press Enter after starting Docker..."
        & docker info | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Err "Docker daemon still not available. Installation aborted."
            exit 1
        }
    }
} catch {
    Write-Err "Docker daemon is not running. Please start Docker Desktop."
    exit 1
}

$composeVersion = & docker compose version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Err "Docker Compose v2 not found. Please install docker-compose-plugin."
    exit 1
}

Write-Ok "Docker environment OK ($(& docker --version), $composeVersion)"

# ============================================================
# [4/6] MySQL Configuration
# ============================================================
Write-Host ""
Write-Info "MySQL Configuration"
Write-Host "  Default: use container MySQL (recommended). Choose custom for external MySQL."
Write-Host ""

$useContainerMysql = Read-Prompt "Use container MySQL? [Y/n]" "Y"
if ($useContainerMysql -match "^n|^N") {
    $dbHost = Read-Prompt "MySQL host" "127.0.0.1"
    $dbPort = Read-Prompt "MySQL port" "3306"
    $dbUser = Read-Prompt "MySQL username" ""
    $dbPass = Read-Prompt "MySQL password" ""
    $dbName = Read-Prompt "Database name" "ascoder"
    $datasourceUrl = "jdbc:mysql://${dbHost}:${dbPort}/${dbName}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
} else {
    $datasourceUrl = ""
}

# ============================================================
# [5/6] LLM Configuration
# ============================================================
Write-Host ""
Write-Info "Configure LLM API"
Write-Host "  Select LLM provider:"
Write-Host "    1) MiniMax"
Write-Host "    2) Anthropic"
Write-Host "    3) OpenAI"
Write-Host ""

$llmChoice = 0
while ($llmChoice -lt 1 -or $llmChoice -gt 3) {
    $llmChoice = Read-Prompt "Select (1-3)"
}

switch ($llmChoice) {
    1 { $llmProvider = "agentscope"; $defaultModel = "MiniMax-M2.7"; $defaultBaseUrl = "https://api.minimaxi.com/anthropic"; $apiKeyVar = "MINIMAX_API_KEY" }
    2 { $llmProvider = "anthropic"; $defaultModel = "claude-sonnet-4-6-20250514"; $defaultBaseUrl = "https://api.anthropic.com"; $apiKeyVar = "ANTHROPIC_API_KEY" }
    3 { $llmProvider = "openai"; $defaultModel = "gpt-4o"; $defaultBaseUrl = "https://api.openai.com/v1"; $apiKeyVar = "OPENAI_API_KEY" }
}

$apiKey = Read-Prompt "API Key ($apiKeyVar)"
$agentModelId = Read-Prompt "Model ID" $defaultModel
$agentBaseUrl = Read-Prompt "Base URL" $defaultBaseUrl

# Optional git token
Write-Host ""
$gitTokenInput = Read-Prompt "Git HTTPS Token (optional, needed for private repos)"
$gitUsernameInput = Read-Prompt "Git Username (default git)" "git"
$gitExtraHostsInput = Read-Prompt "Git Extra Hosts (optional, comma-separated, e.g. git.example.com)"

Write-Ok "Configuration collected."

# ============================================================
# [6/6] Generate Config and Start
# ============================================================
Write-Host ""
Write-Info "Generating config file $installDir\.env ..."

$envFile = Join-Path $installDir ".env"
if (-not (Test-Path $envFile)) {
    $exampleFile = Join-Path $installDir ".env.example"
    if (Test-Path $exampleFile) {
        Copy-Item $exampleFile $envFile
    } else {
        New-Item -ItemType File -Path $envFile | Out-Null
    }
}

# Helper to write/update env vars
function Set-EnvVar($key, $value) {
    $content = Get-Content $envFile -Raw -ErrorAction SilentlyContinue
    if ($content -match "(?m)^$key=") {
        $content = $content -replace "(?m)^$key=.*", "$key=$value"
    } else {
        $content = "$content`n$key=$value"
    }
    Set-Content -Path $envFile -Value $content.TrimEnd() -Encoding UTF8
}

Set-EnvVar "APP_PORT" "5173"
Set-EnvVar "LLM_PROVIDER" $llmProvider
Set-EnvVar "AGENT_MODEL_ID" $agentModelId
Set-EnvVar "AGENT_BASE_URL" $agentBaseUrl
Set-EnvVar "MINIMAX_API_KEY" $(if ($apiKeyVar -eq "MINIMAX_API_KEY") { $apiKey } else { "" })
Set-EnvVar "ANTHROPIC_API_KEY" $(if ($apiKeyVar -eq "ANTHROPIC_API_KEY") { $apiKey } else { "" })
Set-EnvVar "OPENAI_API_KEY" $(if ($apiKeyVar -eq "OPENAI_API_KEY") { $apiKey } else { "" })

if ($datasourceUrl) {
    Set-EnvVar "SPRING_DATASOURCE_URL" $datasourceUrl
    Set-EnvVar "SPRING_DATASOURCE_USERNAME" $dbUser
    Set-EnvVar "SPRING_DATASOURCE_PASSWORD" $dbPass
}

if ($gitTokenInput) {
    Set-EnvVar "GIT_TOKEN" $gitTokenInput
    Set-EnvVar "GIT_USERNAME" $gitUsernameInput
}

if ($gitExtraHostsInput) {
    Set-EnvVar "GIT_EXTRA_HOSTS" $gitExtraHostsInput
}

Write-Ok "Config file generated."

# Start
Write-Info "Starting Ascoder ..."
Push-Location $installDir
& docker compose up -d --build
Pop-Location

Write-Host ""
Write-Ok "Ascoder installation complete!"
Write-Host "  URL: http://localhost:5173"
Write-Host "  Config: $installDir\.env"
Write-Host "  Start/Stop: $installDir\bin\start.ps1 | $installDir\bin\stop.ps1"
Write-Host "  Uninstall: $installDir\uninstall.ps1"
