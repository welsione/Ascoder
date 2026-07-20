# Ascoder 安装引导 TUI + 自动打包工具 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建跨平台安装引导 TUI（Bash + PowerShell）和自动打包工具，实现"解压即安装"的产品化体验。

**Architecture:** install.sh/install.ps1 作为单一安装入口，线性六步流程：欢迎 → 选择目录 → 环境检测与依赖安装 → 数据库配置 → LLM 配置 → 生成配置与服务注册。scripts/package.sh 在开发机构建 JAR + 前端 + 收集 CodeGraph 二进制，输出通用 zip 包。

**Tech Stack:** Bash 4+、PowerShell 5.1+、Spring Boot Maven 构建、Vite 前端构建

---

## 文件结构

| 操作 | 文件路径 | 职责 |
|------|----------|------|
| 创建 | `installer/install.sh` | macOS/Linux 安装引导 TUI |
| 创建 | `installer/install.ps1` | Windows 安装引导 TUI |
| 创建 | `installer/uninstall.sh` | macOS/Linux 卸载 |
| 创建 | `installer/uninstall.ps1` | Windows 卸载 |
| 创建 | `installer/bin/start.sh` | Unix 启动脚本 |
| 创建 | `installer/bin/stop.sh` | Unix 停止脚本 |
| 创建 | `installer/bin/start.ps1` | Windows 启动脚本 |
| 创建 | `installer/bin/stop.ps1` | Windows 停止脚本 |
| 创建 | `installer/conf/application.yml` | Spring Boot 配置模板 |
| 创建 | `installer/README.txt` | 快速上手说明 |
| 创建 | `scripts/package.sh` | 自动打包工具 |

---

### Task 1: Unix 启停脚本

**Files:**
- Create: `installer/bin/start.sh`
- Create: `installer/bin/stop.sh`

- [ ] **Step 1: 创建 start.sh**

```bash
#!/usr/bin/env bash
# Ascoder 启动脚本 (Unix)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$INSTALL_DIR/run/ascoder.pid"
LOG_FILE="$INSTALL_DIR/run/ascoder.log"
ENV_FILE="$INSTALL_DIR/.env"

# 加载 .env
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

mkdir -p "$INSTALL_DIR/run"

if [ -f "$PID_FILE" ]; then
  OLD_PID=$(cat "$PID_FILE")
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "Ascoder is already running (PID: $OLD_PID)"
    exit 1
  fi
  rm -f "$PID_FILE"
fi

# 默认 JVM 参数
JVM_OPTS="${JVM_OPTS:--Xmx512m -Xms256m}"

# 设置 Spring Boot 配置
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://127.0.0.1:3306/ascoder?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}"
SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-ascoder}"
SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-ascoder}"

export SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD

echo "Starting Ascoder..."
nohup java $JVM_OPTS -jar "$INSTALL_DIR/ascoder.jar" > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

# 等待启动
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/api/health >/dev/null 2>&1; then
    APP_PORT="${APP_PORT:-5173}"
    echo "Ascoder started successfully (PID: $(cat "$PID_FILE"))"
    echo "Access: http://localhost:${APP_PORT}"
    echo "Log: $LOG_FILE"
    exit 0
  fi
  sleep 2
done

echo "Warning: Ascoder may still be starting. Check log: $LOG_FILE"
echo "Health check: curl http://127.0.0.1:8080/api/health"
```

- [ ] **Step 2: 创建 stop.sh**

```bash
#!/usr/bin/env bash
# Ascoder 停止脚本 (Unix)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$INSTALL_DIR/run/ascoder.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "Ascoder is not running (PID file not found)"
  exit 0
fi

PID=$(cat "$PID_FILE")

if ! kill -0 "$PID" 2>/dev/null; then
  echo "Ascoder is not running (stale PID file)"
  rm -f "$PID_FILE"
  exit 0
fi

echo "Stopping Ascoder (PID: $PID)..."
kill "$PID"

# 等待优雅关闭（最多 30 秒）
for i in $(seq 1 30); do
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "Ascoder stopped."
    rm -f "$PID_FILE"
    exit 0
  fi
  sleep 1
done

echo "Force killing Ascoder..."
kill -9 "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "Ascoder force stopped."
```

- [ ] **Step 3: 设置可执行权限**

```bash
chmod +x installer/bin/start.sh installer/bin/stop.sh
```

- [ ] **Step 4: 提交**

```bash
git add installer/bin/start.sh installer/bin/stop.sh
git commit -m "feat: add Unix start/stop scripts for installer"
```

---

### Task 2: Windows 启停脚本

**Files:**
- Create: `installer/bin/start.ps1`
- Create: `installer/bin/stop.ps1`

- [ ] **Step 1: 创建 start.ps1**

```powershell
# Ascoder 启动脚本 (Windows)
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$installDir = Split-Path -Parent $scriptDir
$envFile = Join-Path $installDir ".env"
$logFile = Join-Path $installDir "run\ascoder.log"

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

# 创建 run 目录
$newDir = Join-Path $installDir "run"
if (-not (Test-Path $newDir)) {
    New-Item -ItemType Directory -Path $newDir | Out-Null
}

# 默认 JVM 参数
if (-not $env:JVM_OPTS) {
    $env:JVM_OPTS = "-Xmx512m -Xms256m"
}

# 设置 Spring Boot 配置
if (-not $env:SPRING_DATASOURCE_URL) {
    $env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/ascoder?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
}

$jarPath = Join-Path $installDir "ascoder.jar"
Write-Host "Starting Ascoder..."
$process = Start-Process -FilePath "java" -ArgumentList "$env:JVM_OPTS", "-jar", $jarPath -RedirectStandardOutput $logFile -RedirectStandardError (Join-Path $installDir "run\ascoder-error.log") -NoNewWindow -PassThru

# 等待启动
$appPort = if ($env:APP_PORT) { $env:APP_PORT } else { "5173" }
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
    Write-Host "Ascoder started successfully (PID: $($process.Id))"
    Write-Host "Access: http://localhost:$appPort"
    Write-Host "Log: $logFile"
} else {
    Write-Host "Warning: Ascoder may still be starting. Check log: $logFile"
}
```

- [ ] **Step 2: 创建 stop.ps1**

```powershell
# Ascoder 停止脚本 (Windows)
$ErrorActionPreference = "Stop"

Write-Host "Stopping Ascoder..."
try {
    $processes = Get-Process -Name "java" -ErrorAction SilentlyContinue
    if ($processes) {
        $processes | Stop-Process -Force
        Write-Host "Ascoder stopped."
    } else {
        Write-Host "Ascoder is not running."
    }
} catch {
    Write-Error "Failed to stop Ascoder: $_"
}
```

- [ ] **Step 3: 提交**

```bash
git add installer/bin/start.ps1 installer/bin/stop.ps1
git commit -m "feat: add Windows start/stop scripts for installer"
```

---

### Task 3: Spring Boot 配置模板 + README

**Files:**
- Create: `installer/conf/application.yml`
- Create: `installer/README.txt`

- [ ] **Step 1: 创建配置模板 application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: ascoder-backend
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
    open-in-view: false
  flyway:
    enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info

ascoder:
  repo-root: ${REPO_ROOT:./data/repos}
  worktree-root: ${WORKTREE_ROOT:./data/worktrees}
  project-space-root: ${PROJECT_SPACE_ROOT:./data/project-spaces}
  codegraph-root: ${CODEGRAPH_ROOT:./data/codegraph}
  llm-provider: ${LLM_PROVIDER:agentscope}
  agent:
    model-id: ${AGENT_MODEL_ID:MiniMax-M2.7}
    api-key: ${MINIMAX_API_KEY:${ANTHROPIC_API_KEY:}}
    base-url: ${AGENT_BASE_URL:https://api.minimaxi.com/anthropic}
    max-tokens: ${AGENT_MAX_TOKENS:4000}
    timeout-seconds: ${AGENT_TIMEOUT_SECONDS:240}
    tool-timeout-seconds: ${AGENT_TOOL_TIMEOUT_SECONDS:300}
    max-iters: ${AGENT_MAX_ITERS:12}
    stream-core-threads: ${AGENT_STREAM_CORE_THREADS:2}
    stream-max-threads: ${AGENT_STREAM_MAX_THREADS:16}
    stream-queue-capacity: ${AGENT_STREAM_QUEUE_CAPACITY:64}
    sse-timeout-seconds: ${AGENT_SSE_TIMEOUT_SECONDS:600}
    heartbeat-interval-seconds: ${AGENT_HEARTBEAT_INTERVAL_SECONDS:30}
    model-max-attempts: ${AGENT_MODEL_MAX_ATTEMPTS:2}
    tool-max-attempts: ${AGENT_TOOL_MAX_ATTEMPTS:1}
    planning-enabled: ${AGENT_PLANNING_ENABLED:true}
    plan-max-subtasks: ${AGENT_PLAN_MAX_SUBTASKS:10}
  codegraph:
    executable: ${CODEGRAPH_EXECUTABLE:codegraph}
    timeout-seconds: ${CODEGRAPH_TIMEOUT_SECONDS:300}
    index-timeout-seconds: ${CODEGRAPH_INDEX_TIMEOUT_SECONDS:3600}
  git:
    timeout-seconds: ${GIT_TIMEOUT_SECONDS:120}
```

- [ ] **Step 2: 创建 README.txt**

```
==========================================
  Ascoder - 团队代码理解平台
==========================================

快速安装（需要管理员/管理员权限）:

  macOS / Linux:
    chmod +x install.sh
    sudo ./install.sh

  Windows (以管理员身份运行 PowerShell):
    Set-ExecutionPolicy Bypass -Scope Process
    .\install.ps1

安装完成后访问: http://localhost:5173

启停命令:
  Unix:   bin/start.sh / bin/stop.sh
  Windows: bin\start.ps1 / bin\stop.ps1

卸载:
  Unix:    sudo ./uninstall.sh
  Windows: .\uninstall.ps1

配置文件: .env
日志目录: run/

更多信息: https://github.com/welsione/ascoder
```

- [ ] **Step 3: 提交**

```bash
git add installer/conf/application.yml installer/README.txt
git commit -m "feat: add Spring Boot config template and README for installer"
```

---

### Task 4: macOS/Linux 安装引导 TUI

**Files:**
- Create: `installer/install.sh`

- [ ] **Step 1: 创建 install.sh — 头部和工具函数**

```bash
#!/usr/bin/env bash
# Ascoder 安装引导 TUI (macOS / Linux)
set -euo pipefail

VERSION="0.1.0"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

# 安装脚本所在目录（即 zip 解压后的 ascoder/ 目录）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 工具函数
info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

prompt() {
  local var="$1" message="$2" default="${3:-}"
  if [ -n "$default" ]; then
    read -r -p "$(echo -e "${BOLD}$message${NC} [$default]: ")" "$var"
    eval "$var=\${$var:-$default}"
  else
    read -r -p "$(echo -e "${BOLD}$message${NC}: ")" "$var"
  fi
}

confirm() {
  local message="$1"
  local answer
  read -r -p "$(echo -e "${BOLD}$message${NC} [Y/n]: ")" answer
  case "$answer" in
    n|N) return 1 ;;
    *) return 0 ;;
  esac
}

step_header() {
  local step="$1" title="$2"
  echo ""
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BOLD}  [$step] $title${NC}"
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}
```

- [ ] **Step 2: 添加 [1/6] 欢迎信息 + [2/6] 选择安装目录**

在 install.sh 末尾追加：

```bash
# ============================================================
# [1/6] 欢迎
# ============================================================
step_header "1/6" "欢迎使用 Ascoder 安装向导"

echo ""
echo "  Ascoder v${VERSION} - 团队代码理解平台"
echo ""
echo "  本向导将引导您完成以下步骤："
echo "    1. 选择安装目录"
echo "    2. 检测并安装依赖 (Java, Git, CodeGraph)"
echo "    3. 配置 MySQL 数据库连接"
echo "    4. 配置 LLM API"
echo "    5. 生成配置并注册系统服务"
echo ""

if ! confirm "是否开始安装?"; then
  echo "安装已取消。"
  exit 0
fi

# ============================================================
# [2/6] 选择安装目录
# ============================================================
step_header "2/6" "选择安装目录"

DEFAULT_INSTALL_DIR="/opt/ascoder"
prompt INSTALL_DIR "请输入安装目录" "$DEFAULT_INSTALL_DIR"

# 展开用户目录
INSTALL_DIR="${INSTALL_DIR/#\~/$HOME}"

if [ -d "$INSTALL_DIR" ]; then
  warn "目录 $INSTALL_DIR 已存在。"
  if ! confirm "是否覆盖安装?"; then
    echo "安装已取消。"
    exit 0
  fi
else
  info "将创建目录: $INSTALL_DIR"
fi

sudo_prefix=""
if [ ! -w "$(dirname "$INSTALL_DIR" 2>/dev/null || echo "/")" ]; then
  sudo_prefix="sudo"
  info "安装目录需要管理员权限。"
fi

# 复制文件到安装目录
info "正在复制文件到 $INSTALL_DIR ..."
$sudo_prefix mkdir -p "$INSTALL_DIR"
$sudo_prefix cp -r "$SCRIPT_DIR/"* "$INSTALL_DIR/"
$sudo_prefix cp -r "$SCRIPT_DIR"/.env.example "$INSTALL_DIR/.env.example" 2>/dev/null || true
ok "文件复制完成。"
```

- [ ] **Step 3: 添加 [3/6] 环境检测与依赖安装**

在 install.sh 末尾追加：

```bash
# ============================================================
# [3/6] 环境检测 & 依赖安装
# ============================================================
step_header "3/6" "环境检测 & 依赖安装"

detect_os() {
  if [ "$(uname)" = "Darwin" ]; then
    echo "macos"
  elif [ -f /etc/debian_version ]; then
    echo "debian"
  elif [ -f /etc/redhat-release ]; then
    echo "redhat"
  else
    echo "linux"
  fi
}

OS_TYPE=$(detect_os)
info "检测到系统: $OS_TYPE"

# --- Java ---
check_java() {
  if command -v java >/dev/null 2>&1; then
    local version
    version=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
    if [ "$version" -ge 17 ] 2>/dev/null || [ "$version" -eq 1 ] 2>/dev/null; then
      # version=1 means JDK 1.8 format, check minor
      local full
      full=$(java -version 2>&1 | head -1)
      if echo "$full" | grep -qE '"1[7-9]|"[2-9][0-9]'; then
        return 0
      fi
    fi
    if echo "$full" 2>/dev/null | grep -qE '"1[7-9]|"[2-9][0-9]' || java -version 2>&1 | head -1 | grep -qE '"1[7-9]|"[2-9][0-9]'; then
      return 0
    fi
    warn "Java 版本低于 17，需要升级。"
    return 1
  fi
  return 1
}

install_java() {
  info "正在安装 Java 17+ ..."
  case "$OS_TYPE" in
    debian)
      $sudo_prefix apt-get update -qq && $sudo_prefix apt-get install -y -qq openjdk-17-jre-headless 2>/dev/null
      ;;
    redhat)
      $sudo_prefix yum install -y java-17-openjdk-headless 2>/dev/null
      ;;
    macos)
      if command -v brew >/dev/null 2>&1; then
        brew install openjdk@17 2>/dev/null
      else
        error "未检测到 Homebrew，请手动安装 Java 17+:"
        error "  访问 https://adoptium.net/ 下载安装，或先安装 Homebrew: https://brew.sh"
        return 1
      fi
      ;;
    *)
      error "不支持的系统，请手动安装 Java 17+"
      return 1
      ;;
  esac
}

if check_java; then
  ok "Java 17+ 已安装 ($(java -version 2>&1 | head -1))"
else
  if install_java; then
    ok "Java 17+ 安装成功"
  else
    error "Java 17+ 安装失败，请手动安装后重新运行本脚本。"
    echo ""
    echo "  手动安装指南："
    echo "    Linux (Debian/Ubuntu): sudo apt install openjdk-17-jre-headless"
    echo "    Linux (RHEL/CentOS):   sudo yum install java-17-openjdk-headless"
    echo "    macOS:                 brew install openjdk@17"
    echo "    通用:                  https://adoptium.net/"
    echo ""
    read -r -p "安装完成后按回车继续..."
    if ! check_java; then
      error "Java 仍不可用，安装中止。"
      exit 1
    fi
  fi
fi

# --- Git ---
if command -v git >/dev/null 2>&1; then
  ok "Git 已安装 ($(git --version))"
else
  info "正在安装 Git ..."
  case "$OS_TYPE" in
    debian)
      $sudo_prefix apt-get install -y -qq git 2>/dev/null
      ;;
    redhat)
      $sudo_prefix yum install -y git 2>/dev/null
      ;;
    macos)
      if command -v brew >/dev/null 2>&1; then
        brew install git 2>/dev/null
      else
        xcode-select --install 2>/dev/null || true
      fi
      ;;
    *)
      error "不支持的系统，请手动安装 Git"
      ;;
  esac

  if command -v git >/dev/null 2>&1; then
    ok "Git 安装成功"
  else
    error "Git 安装失败，请手动安装后重新运行本脚本。"
    echo ""
    echo "  手动安装指南："
    echo "    Linux (Debian/Ubuntu): sudo apt install git"
    echo "    Linux (RHEL/CentOS):   sudo yum install git"
    echo "    macOS:                 xcode-select --install 或 brew install git"
    echo "    通用:                  https://git-scm.com/downloads"
    echo ""
    read -r -p "安装完成后按回车继续..."
    if ! command -v git >/dev/null 2>&1; then
      error "Git 仍不可用，安装中止。"
      exit 1
    fi
  fi
fi

# --- CodeGraph CLI ---
ARCH=$(uname -m)
case "$ARCH" in
  x86_64)  CG_BIN="codegraph-linux-amd64" ;;
  aarch64) CG_BIN="codegraph-linux-arm64" ;;
  arm64)   CG_BIN="codegraph-darwin-arm64" ;;
  *)       CG_BIN="" ;;
esac

if [ "$(uname)" = "Darwin" ] && [ "$ARCH" = "x86_64" ]; then
  CG_BIN="codegraph-darwin-amd64"
fi

CODEGRAPH_INSTALLED=false
if command -v codegraph >/dev/null 2>&1; then
  ok "CodeGraph CLI 已安装 ($(codegraph --version 2>/dev/null || echo 'installed'))"
  CODEGRAPH_INSTALLED=true
elif [ -n "$CG_BIN" ] && [ -f "$INSTALL_DIR/lib/codegraph/$CG_BIN" ]; then
  info "正在从本地安装 CodeGraph CLI ..."
  $sudo_prefix cp "$INSTALL_DIR/lib/codegraph/$CG_BIN" "$INSTALL_DIR/bin/codegraph"
  $sudo_prefix chmod +x "$INSTALL_DIR/bin/codegraph"
  # 添加到 PATH（写入 profile）
  if ! echo "$PATH" | grep -q "$INSTALL_DIR/bin"; then
    export PATH="$INSTALL_DIR/bin:$PATH"
    echo "export PATH=\"$INSTALL_DIR/bin:\$PATH\"" >> "$HOME/.bashrc" 2>/dev/null || true
    echo "export PATH=\"$INSTALL_DIR/bin:\$PATH\"" >> "$HOME/.zshrc" 2>/dev/null || true
  fi
  if command -v codegraph >/dev/null 2>&1 || [ -x "$INSTALL_DIR/bin/codegraph" ]; then
    ok "CodeGraph CLI 安装成功"
    CODEGRAPH_INSTALLED=true
  else
    warn "CodeGraph CLI 安装失败，可稍后手动安装。"
  fi
else
  warn "未找到匹配的 CodeGraph CLI 二进制 ($CG_BIN)，可稍后手动安装。"
  warn "安装方法: npm install -g @colbymchenry/codegraph"
fi
```

- [ ] **Step 4: 添加 [4/6] 数据库配置 + [5/6] LLM 配置**

在 install.sh 末尾追加：

```bash
# ============================================================
# [4/6] 数据库配置
# ============================================================
step_header "4/6" "配置 MySQL 数据库连接"

echo "  请输入远程 MySQL 连接信息。"
echo ""

DB_CONNECTED=false
while [ "$DB_CONNECTED" = "false" ]; do
  prompt DB_HOST "MySQL 主机地址" "127.0.0.1"
  prompt DB_PORT "MySQL 端口" "3306"
  prompt DB_USER "MySQL 用户名" ""
  prompt DB_PASS "MySQL 密码" ""
  prompt DB_NAME "数据库名" "ascoder"

  info "正在测试 MySQL 连接 (${DB_HOST}:${DB_PORT}) ..."

  if command -v mysql >/dev/null 2>&1; then
    if mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" -e "SELECT 1" "$DB_NAME" >/dev/null 2>&1; then
      ok "MySQL 连接成功！"
      DB_CONNECTED=true
    else
      error "MySQL 连接失败，请检查连接信息。"
      if ! confirm "是否重试?"; then
        error "安装中止。"
        exit 1
      fi
    fi
  else
    warn "未检测到 mysql 客户端，无法测试连接。"
    warn "请确保 MySQL 服务器可用，配置将在启动时验证。"
    if confirm "继续安装?"; then
      DB_CONNECTED=true
    else
      if ! confirm "是否重新输入?"; then
        error "安装中止。"
        exit 1
      fi
    fi
  fi
done

SPRING_DATASOURCE_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"

# ============================================================
# [5/6] LLM 配置
# ============================================================
step_header "5/6" "配置 LLM API"

echo "  选择 LLM 服务商："
echo "    1) MiniMax"
echo "    2) Anthropic"
echo "    3) OpenAI"
echo ""

LLM_CHOICE=""
while [ -z "$LLM_CHOICE" ] || [ "$LLM_CHOICE" -lt 1 ] 2>/dev/null || [ "$LLM_CHOICE" -gt 3 ] 2>/dev/null; do
  prompt LLM_CHOICE "请选择 (1-3)" ""
  case "$LLM_CHOICE" in
    1) LLM_PROVIDER="agentscope"; DEFAULT_MODEL="MiniMax-M2.7"; DEFAULT_BASE_URL="https://api.minimaxi.com/anthropic"; API_KEY_VAR="MINIMAX_API_KEY" ;;
    2) LLM_PROVIDER="anthropic"; DEFAULT_MODEL="claude-sonnet-4-6-20250514"; DEFAULT_BASE_URL="https://api.anthropic.com"; API_KEY_VAR="ANTHROPIC_API_KEY" ;;
    3) LLM_PROVIDER="openai"; DEFAULT_MODEL="gpt-4o"; DEFAULT_BASE_URL="https://api.openai.com/v1"; API_KEY_VAR="OPENAI_API_KEY" ;;
    *) LLM_CHOICE="" ;;
  esac
done

prompt API_KEY "API Key (${API_KEY_VAR})" ""
prompt AGENT_MODEL_ID "Model ID" "$DEFAULT_MODEL"
prompt AGENT_BASE_URL "Base URL" "$DEFAULT_BASE_URL"

ok "LLM 配置完成。"
```

- [ ] **Step 5: 添加 [6/6] 生成配置与服务注册**

在 install.sh 末尾追加：

```bash
# ============================================================
# [6/6] 生成配置 & 注册服务
# ============================================================
step_header "6/6" "生成配置 & 注册服务"

# --- 生成 .env ---
ENV_FILE="$INSTALL_DIR/.env"
info "正在生成配置文件 $ENV_FILE ..."

cat > /tmp/ascoder-env.tmp <<EOF
# Ascoder 配置 - 由安装向导生成
# 修改后需重启服务: bin/stop.sh && bin/start.sh

# --- Application ---
APP_PORT=5173

# --- MySQL ---
SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME=$DB_USER
SPRING_DATASOURCE_PASSWORD=$DB_PASS

# --- LLM Provider ---
LLM_PROVIDER=$LLM_PROVIDER
AGENT_MODEL_ID=$AGENT_MODEL_ID
AGENT_BASE_URL=$AGENT_BASE_URL
AGENT_MAX_TOKENS=4000
AGENT_TIMEOUT_SECONDS=240
AGENT_TOOL_TIMEOUT_SECONDS=300
AGENT_MAX_ITERS=12
AGENT_SSE_TIMEOUT_SECONDS=600

# --- LLM API Keys ---
MINIMAX_API_KEY=$([ "$API_KEY_VAR" = "MINIMAX_API_KEY" ] && echo "$API_KEY" || echo "")
ANTHROPIC_API_KEY=$([ "$API_KEY_VAR" = "ANTHROPIC_API_KEY" ] && echo "$API_KEY" || echo "")
OPENAI_API_KEY=$([ "$API_KEY_VAR" = "OPENAI_API_KEY" ] && echo "$API_KEY" || echo "")

# --- CodeGraph ---
CODEGRAPH_EXECUTABLE=$([ "$CODEGRAPH_INSTALLED" = "true" ] && echo "$INSTALL_DIR/bin/codegraph" || echo "codegraph")
CODEGRAPH_TIMEOUT_SECONDS=300
CODEGRAPH_INDEX_TIMEOUT_SECONDS=3600

# --- Git ---
GIT_TIMEOUT_SECONDS=120

# --- JVM Options ---
JVM_OPTS=-Xmx512m -Xms256m

# --- Data directories ---
REPO_ROOT=$INSTALL_DIR/data/repos
WORKTREE_ROOT=$INSTALL_DIR/data/worktrees
PROJECT_SPACE_ROOT=$INSTALL_DIR/data/project-spaces
CODEGRAPH_ROOT=$INSTALL_DIR/data/codegraph
EOF

$sudo_prefix mv /tmp/ascoder-env.tmp "$ENV_FILE"
$sudo_prefix chmod 600 "$ENV_FILE"
ok "配置文件已生成。"

# --- 创建数据目录 ---
$sudo_prefix mkdir -p "$INSTALL_DIR/data/repos" "$INSTALL_DIR/data/worktrees" "$INSTALL_DIR/data/project-spaces" "$INSTALL_DIR/data/codegraph" "$INSTALL_DIR/run"

# --- 注册系统服务 ---
SERVICE_REGISTERED=false
info "正在注册系统服务 ..."

if [ "$OS_TYPE" = "macos" ]; then
  PLIST_FILE="$HOME/Library/LaunchAgents/com.ascoder.service.plist"
  cat > /tmp/ascoder-plist.tmp <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>com.ascoder.service</string>
  <key>ProgramArguments</key>
  <array>
    <string>$INSTALL_DIR/bin/start.sh</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>WorkingDirectory</key>
  <string>$INSTALL_DIR</string>
  <key>StandardOutPath</key>
  <string>$INSTALL_DIR/run/ascoder.log</string>
  <key>StandardErrorPath</key>
  <string>$INSTALL_DIR/run/ascoder-error.log</string>
</dict>
</plist>
EOF
  mkdir -p "$HOME/Library/LaunchAgents" 2>/dev/null || true
  if mv /tmp/ascoder-plist.tmp "$PLIST_FILE" 2>/dev/null; then
    launchctl load "$PLIST_FILE" 2>/dev/null || true
    SERVICE_REGISTERED=true
    ok "launchd 服务已注册。"
  else
    rm -f /tmp/ascoder-plist.tmp
    warn "launchd 服务注册失败，将使用启停脚本模式。"
  fi

elif [ "$OS_TYPE" = "debian" ] || [ "$OS_TYPE" = "redhat" ]; then
  UNIT_FILE="/etc/systemd/system/ascoder.service"
  cat > /tmp/ascoder-service.tmp <<EOF
[Unit]
Description=Ascoder - Team Code Understanding Platform
After=network.target mysql.service

[Service]
Type=simple
User=$(whoami)
WorkingDirectory=$INSTALL_DIR
EnvironmentFile=$INSTALL_DIR/.env
ExecStart=$(command -v java) \$JVM_OPTS -jar $INSTALL_DIR/ascoder.jar
ExecStop=$INSTALL_DIR/bin/stop.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
  if $sudo_prefix mv /tmp/ascoder-service.tmp "$UNIT_FILE" 2>/dev/null && $sudo_prefix systemctl daemon-reload 2>/dev/null; then
    $sudo_prefix systemctl enable ascoder 2>/dev/null || true
    SERVICE_REGISTERED=true
    ok "systemd 服务已注册。"
  else
    rm -f /tmp/ascoder-service.tmp
    warn "systemd 服务注册失败，将使用启停脚本模式。"
  fi
fi

# --- 启动服务 ---
info "正在启动 Ascoder ..."
if [ "$SERVICE_REGISTERED" = "true" ]; then
  if [ "$OS_TYPE" = "macos" ]; then
    launchctl start com.ascoder.service 2>/dev/null || "$INSTALL_DIR/bin/start.sh"
  else
    $sudo_prefix systemctl start ascoder 2>/dev/null || "$INSTALL_DIR/bin/start.sh"
  fi
else
  "$INSTALL_DIR/bin/start.sh"
fi

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  ✓ Ascoder 安装完成！${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  访问地址: http://localhost:5173"
echo "  配置文件: $INSTALL_DIR/.env"
echo "  启停命令: $INSTALL_DIR/bin/start.sh | $INSTALL_DIR/bin/stop.sh"
if [ "$SERVICE_REGISTERED" = "true" ]; then
  echo "  系统服务: 已注册 (开机自启)"
else
  echo "  系统服务: 未注册 (使用启停脚本)"
fi
echo ""
echo "  卸载: $INSTALL_DIR/uninstall.sh"
echo ""
```

- [ ] **Step 6: 设置可执行权限**

```bash
chmod +x installer/install.sh
```

- [ ] **Step 7: 提交**

```bash
git add installer/install.sh
git commit -m "feat: add macOS/Linux installer TUI with 6-step guided setup"
```

---

### Task 5: macOS/Linux 卸载脚本

**Files:**
- Create: `installer/uninstall.sh`

- [ ] **Step 1: 创建 uninstall.sh**

```bash
#!/usr/bin/env bash
# Ascoder 卸载脚本 (macOS / Linux)
set -euo pipefail

RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m'

INSTALL_DIR="${1:-}"

if [ -z "$INSTALL_DIR" ]; then
  # 尝试从已知位置查找
  if [ -f "/opt/ascoder/ascoder.jar" ]; then
    INSTALL_DIR="/opt/ascoder"
  elif [ -f "$HOME/ascoder/ascoder.jar" ]; then
    INSTALL_DIR="$HOME/ascoder"
  else
    echo -e "${RED}请指定安装目录: $0 <install-dir>${NC}"
    exit 1
  fi
fi

echo -e "${BOLD}Ascoder 卸载${NC}"
echo ""
echo "  将删除以下内容："
echo "    - 安装目录: $INSTALL_DIR"
echo "    - 系统服务注册"
echo ""
echo -e "${RED}警告: 此操作不可恢复！所有数据（仓库、索引）将被删除。${NC}"
echo ""

read -r -p "$(echo -e "${BOLD}确认卸载? 输入 YES 继续:${NC} ")" confirm
if [ "$confirm" != "YES" ]; then
  echo "卸载已取消。"
  exit 0
fi

# 停止服务
if [ -f "$INSTALL_DIR/bin/stop.sh" ]; then
  echo "正在停止 Ascoder ..."
  "$INSTALL_DIR/bin/stop.sh" 2>/dev/null || true
fi

# 注销系统服务
if [ -f "$HOME/Library/LaunchAgents/com.ascoder.service.plist" ]; then
  echo "正在注销 launchd 服务 ..."
  launchctl unload "$HOME/Library/LaunchAgents/com.ascoder.service.plist" 2>/dev/null || true
  rm -f "$HOME/Library/LaunchAgents/com.ascoder.service.plist"
fi

if [ -f "/etc/systemd/system/ascoder.service" ]; then
  echo "正在注销 systemd 服务 ..."
  sudo systemctl stop ascoder 2>/dev/null || true
  sudo systemctl disable ascoder 2>/dev/null || true
  sudo rm -f /etc/systemd/system/ascoder.service
  sudo systemctl daemon-reload 2>/dev/null || true
fi

# 删除安装目录
sudo_prefix=""
if [ ! -w "$INSTALL_DIR" ]; then
  sudo_prefix="sudo"
fi

echo "正在删除安装目录 $INSTALL_DIR ..."
$sudo_prefix rm -rf "$INSTALL_DIR"

echo ""
echo "Ascoder 已完全卸载。"
```

- [ ] **Step 2: 设置可执行权限**

```bash
chmod +x installer/uninstall.sh
```

- [ ] **Step 3: 提交**

```bash
git add installer/uninstall.sh
git commit -m "feat: add macOS/Linux uninstall script"
```

---

### Task 6: Windows 安装引导 TUI

**Files:**
- Create: `installer/install.ps1`

- [ ] **Step 1: 创建 install.ps1 — 头部和 [1/6]-[2/6]**

```powershell
# Ascoder 安装引导 TUI (Windows)
# 以管理员身份运行 PowerShell
param()
$ErrorActionPreference = "Stop"

$VERSION = "0.1.0"

# 安装脚本所在目录
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-StepHeader($step, $title) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor White
    Write-Host "  [$step] $title" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor White
    Write-Host ""
}

function Read-Prompt($message, $default = "") {
    if ($default) {
        Write-Host "$message [$default]: " -NoNewline -ForegroundColor Yellow
    } else {
        Write-Host "$message: " -NoNewline -ForegroundColor Yellow
    }
    $input = Read-Host
    if ($input -and $input.Trim()) {
        return $input.Trim()
    }
    return $default
}

function Confirm-YesNo($message) {
    Write-Host "$message [Y/n]: " -NoNewline -ForegroundColor Yellow
    $answer = Read-Host
    return ($answer -notmatch "^n|^N")
}

# ============================================================
# [1/6] 欢迎
# ============================================================
Write-StepHeader "1/6" "欢迎使用 Ascoder 安装向导"

Write-Host "  Ascoder v$VERSION - 团队代码理解平台"
Write-Host ""
Write-Host "  本向导将引导您完成以下步骤："
Write-Host "    1. 选择安装目录"
Write-Host "    2. 检测并安装依赖 (Java, Git, CodeGraph)"
Write-Host "    3. 配置 MySQL 数据库连接"
Write-Host "    4. 配置 LLM API"
Write-Host "    5. 生成配置并注册系统服务"
Write-Host ""

if (-not (Confirm-YesNo "是否开始安装?")) {
    Write-Host "安装已取消。"
    exit 0
}

# ============================================================
# [2/6] 选择安装目录
# ============================================================
Write-StepHeader "2/6" "选择安装目录"

$defaultInstallDir = "C:\Ascoder"
$installDir = Read-Prompt "请输入安装目录" $defaultInstallDir

if (Test-Path $installDir) {
    Write-Host "目录 $installDir 已存在。" -ForegroundColor Yellow
    if (-not (Confirm-YesNo "是否覆盖安装?")) {
        Write-Host "安装已取消。"
        exit 0
    }
} else {
    Write-Host "将创建目录: $installDir" -ForegroundColor Blue
}

Write-Host "正在复制文件到 $installDir ..." -ForegroundColor Blue
New-Item -ItemType Directory -Path $installDir -Force | Out-Null
Copy-Item -Path "$scriptDir\*" -Destination $installDir -Recurse -Force
Write-Host "文件复制完成。" -ForegroundColor Green
```

- [ ] **Step 2: 添加 [3/6] 环境检测与依赖安装**

在 install.ps1 末尾追加：

```powershell
# ============================================================
# [3/6] 环境检测 & 依赖安装
# ============================================================
Write-StepHeader "3/6" "环境检测 & 依赖安装"

# --- Java ---
$javaExe = Get-Command java -ErrorAction SilentlyContinue
if ($javaExe) {
    $javaVer = & java -version 2>&1 | Select-Object -First 1
    Write-Host "[OK] Java 已安装 ($javaVer)" -ForegroundColor Green
} else {
    Write-Host "[INFO] 正在安装 Java 17+ ..." -ForegroundColor Blue
    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if ($winget) {
        & winget install --id EclipseAdoptium.Temurin.17.JRE --accept-package-agreements --accept-source-agreements
    } else {
        Write-Host "[ERROR] 未检测到 winget，请手动安装 Java 17+" -ForegroundColor Red
        Write-Host "  下载: https://adoptium.net/"
        Read-Host "安装完成后按回车继续..."
    }

    $javaExe = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaExe) {
        Write-Host "[ERROR] Java 仍不可用，安装中止。" -ForegroundColor Red
        exit 1
    }
    Write-Host "[OK] Java 安装成功" -ForegroundColor Green
}

# --- Git ---
$gitExe = Get-Command git -ErrorAction SilentlyContinue
if ($gitExe) {
    Write-Host "[OK] Git 已安装 ($(git --version))" -ForegroundColor Green
} else {
    Write-Host "[INFO] 正在安装 Git ..." -ForegroundColor Blue
    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if ($winget) {
        & winget install --id Git.Git --accept-package-agreements --accept-source-agreements
    } else {
        Write-Host "[ERROR] 未检测到 winget，请手动安装 Git" -ForegroundColor Red
        Write-Host "  下载: https://git-scm.com/download/win"
        Read-Host "安装完成后按回车继续..."
    }

    $gitExe = Get-Command git -ErrorAction SilentlyContinue
    if (-not $gitExe) {
        Write-Host "[ERROR] Git 仍不可用，安装中止。" -ForegroundColor Red
        exit 1
    }
    Write-Host "[OK] Git 安装成功" -ForegroundColor Green
}

# --- CodeGraph CLI ---
$codegraphExe = Get-Command codegraph -ErrorAction SilentlyContinue
$codegraphInstalled = $false
if ($codegraphExe) {
    Write-Host "[OK] CodeGraph CLI 已安装" -ForegroundColor Green
    $codegraphInstalled = $true
} else {
    $cgBin = Join-Path $installDir "lib\codegraph\codegraph-windows-amd64.exe"
    if (Test-Path $cgBin) {
        Write-Host "[INFO] 正在从本地安装 CodeGraph CLI ..." -ForegroundColor Blue
        $destBin = Join-Path $installDir "bin\codegraph.exe"
        Copy-Item -Path $cgBin -Destination $destBin -Force
        # 添加到 PATH
        $pathDir = Join-Path $installDir "bin"
        $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($currentPath -notlike "*$pathDir*") {
            [Environment]::SetEnvironmentVariable("Path", "$currentPath;$pathDir", "User")
            $env:Path = "$env:Path;$pathDir"
        }
        Write-Host "[OK] CodeGraph CLI 安装成功" -ForegroundColor Green
        $codegraphInstalled = $true
    } else {
        Write-Host "[WARN] 未找到 CodeGraph CLI 二进制，可稍后手动安装。" -ForegroundColor Yellow
        Write-Host "  安装方法: npm install -g @colbymchenry/codegraph" -ForegroundColor Yellow
    }
}
```

- [ ] **Step 3: 添加 [4/6]-[6/6]**

在 install.ps1 末尾追加：

```powershell
# ============================================================
# [4/6] 数据库配置
# ============================================================
Write-StepHeader "4/6" "配置 MySQL 数据库连接"

Write-Host "  请输入远程 MySQL 连接信息。"
Write-Host ""

$dbConnected = $false
while (-not $dbConnected) {
    $dbHost = Read-Prompt "MySQL 主机地址" "127.0.0.1"
    $dbPort = Read-Prompt "MySQL 端口" "3306"
    $dbUser = Read-Prompt "MySQL 用户名" ""
    $dbPass = Read-Prompt "MySQL 密码" ""
    $dbName = Read-Prompt "数据库名" "ascoder"

    Write-Host "正在测试 MySQL 连接 ($dbHost`:$dbPort) ..." -ForegroundColor Blue

    $mysqlExe = Get-Command mysql -ErrorAction SilentlyContinue
    if ($mysqlExe) {
        $testResult = & mysql -h $dbHost -P $dbPort -u $dbUser -p"$dbPass" -e "SELECT 1" $dbName 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[OK] MySQL 连接成功！" -ForegroundColor Green
            $dbConnected = $true
        } else {
            Write-Host "[ERROR] MySQL 连接失败: $testResult" -ForegroundColor Red
            if (-not (Confirm-YesNo "是否重试?")) {
                Write-Host "安装中止。" -ForegroundColor Red
                exit 1
            }
        }
    } else {
        Write-Host "[WARN] 未检测到 mysql 客户端，无法测试连接。" -ForegroundColor Yellow
        Write-Host "  请确保 MySQL 服务器可用，配置将在启动时验证。" -ForegroundColor Yellow
        if (Confirm-YesNo "继续安装?") {
            $dbConnected = $true
        } else {
            if (-not (Confirm-YesNo "是否重新输入?")) {
                Write-Host "安装中止。" -ForegroundColor Red
                exit 1
            }
        }
    }
}

$datasourceUrl = "jdbc:mysql://$dbHost`:$dbPort/$dbName`?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"

# ============================================================
# [5/6] LLM 配置
# ============================================================
Write-StepHeader "5/6" "配置 LLM API"

Write-Host "  选择 LLM 服务商："
Write-Host "    1) MiniMax"
Write-Host "    2) Anthropic"
Write-Host "    3) OpenAI"
Write-Host ""

$llmChoice = 0
while ($llmChoice -lt 1 -or $llmChoice -gt 3) {
    $llmChoice = Read-Prompt "请选择 (1-3)"
}

switch ($llmChoice) {
    1 { $llmProvider = "agentscope"; $defaultModel = "MiniMax-M2.7"; $defaultBaseUrl = "https://api.minimaxi.com/anthropic"; $apiKeyVar = "MINIMAX_API_KEY" }
    2 { $llmProvider = "anthropic"; $defaultModel = "claude-sonnet-4-6-20250514"; $defaultBaseUrl = "https://api.anthropic.com"; $apiKeyVar = "ANTHROPIC_API_KEY" }
    3 { $llmProvider = "openai"; $defaultModel = "gpt-4o"; $defaultBaseUrl = "https://api.openai.com/v1"; $apiKeyVar = "OPENAI_API_KEY" }
}

$apiKey = Read-Prompt "API Key ($apiKeyVar)"
$agentModelId = Read-Prompt "Model ID" $defaultModel
$agentBaseUrl = Read-Prompt "Base URL" $defaultBaseUrl

Write-Host "[OK] LLM 配置完成。" -ForegroundColor Green

# ============================================================
# [6/6] 生成配置 & 注册服务
# ============================================================
Write-StepHeader "6/6" "生成配置 & 注册服务"

# --- 生成 .env ---
$envFile = Join-Path $installDir ".env"
Write-Host "正在生成配置文件 $envFile ..." -ForegroundColor Blue

$minimaxKey = if ($apiKeyVar -eq "MINIMAX_API_KEY") { $apiKey } else { "" }
$anthropicKey = if ($apiKeyVar -eq "ANTHROPIC_API_KEY") { $apiKey } else { "" }
$openaiKey = if ($apiKeyVar -eq "OPENAI_API_KEY") { $apiKey } else { "" }
$codegraphExePath = if ($codegraphInstalled) { Join-Path $installDir "bin\codegraph.exe" } else { "codegraph" }

$envContent = @"
# Ascoder 配置 - 由安装向导生成
# 修改后需重启服务: bin\stop.ps1 && bin\start.ps1

# --- Application ---
APP_PORT=5173

# --- MySQL ---
SPRING_DATASOURCE_URL=$datasourceUrl
SPRING_DATASOURCE_USERNAME=$dbUser
SPRING_DATASOURCE_PASSWORD=$dbPass

# --- LLM Provider ---
LLM_PROVIDER=$llmProvider
AGENT_MODEL_ID=$agentModelId
AGENT_BASE_URL=$agentBaseUrl
AGENT_MAX_TOKENS=4000
AGENT_TIMEOUT_SECONDS=240
AGENT_TOOL_TIMEOUT_SECONDS=300
AGENT_MAX_ITERS=12
AGENT_SSE_TIMEOUT_SECONDS=600

# --- LLM API Keys ---
MINIMAX_API_KEY=$minimaxKey
ANTHROPIC_API_KEY=$anthropicKey
OPENAI_API_KEY=$openaiKey

# --- CodeGraph ---
CODEGRAPH_EXECUTABLE=$codegraphExePath
CODEGRAPH_TIMEOUT_SECONDS=300
CODEGRAPH_INDEX_TIMEOUT_SECONDS=3600

# --- Git ---
GIT_TIMEOUT_SECONDS=120

# --- JVM Options ---
JVM_OPTS=-Xmx512m -Xms256m

# --- Data directories ---
REPO_ROOT=$installDir\data\repos
WORKTREE_ROOT=$installDir\data\worktrees
PROJECT_SPACE_ROOT=$installDir\data\project-spaces
CODEGRAPH_ROOT=$installDir\data\codegraph
"@

Set-Content -Path $envFile -Value $envContent -Encoding UTF8
Write-Host "[OK] 配置文件已生成。" -ForegroundColor Green

# --- 创建数据目录 ---
New-Item -ItemType Directory -Path "$installDir\data\repos" -Force | Out-Null
New-Item -ItemType Directory -Path "$installDir\data\worktrees" -Force | Out-Null
New-Item -ItemType Directory -Path "$installDir\data\project-spaces" -Force | Out-Null
New-Item -ItemType Directory -Path "$installDir\data\codegraph" -Force | Out-Null
New-Item -ItemType Directory -Path "$installDir\run" -Force | Out-Null

# --- 注册 Windows 服务 ---
$serviceRegistered = $false
Write-Host "正在注册系统服务 ..." -ForegroundColor Blue

# 使用 sc.exe 创建服务
$jarPath = Join-Path $installDir "ascoder.jar"
$javaPath = (Get-Command java).Source

try {
    & sc.exe create Ascoder binPath= "$javaPath -jar `"$jarPath`"" start= auto DisplayName= "Ascoder Service" 2>$null
    if ($LASTEXITCODE -eq 0) {
        # 设置环境变量给服务
        & sc.exe failure Ascoder reset= 60 actions= restart/5000 2>$null
        $serviceRegistered = $true
        Write-Host "[OK] Windows 服务已注册。" -ForegroundColor Green
    } else {
        Write-Host "[WARN] Windows 服务注册失败，将使用启停脚本模式。" -ForegroundColor Yellow
    }
} catch {
    Write-Host "[WARN] Windows 服务注册失败，将使用启停脚本模式。" -ForegroundColor Yellow
}

# --- 启动服务 ---
Write-Host "正在启动 Ascoder ..." -ForegroundColor Blue
if ($serviceRegistered) {
    & sc.exe start Ascoder 2>$null
} else {
    & "$installDir\bin\start.ps1"
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  [OK] Ascoder 安装完成！" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  访问地址: http://localhost:5173"
Write-Host "  配置文件: $installDir\.env"
Write-Host "  启停命令: $installDir\bin\start.ps1 | $installDir\bin\stop.ps1"
if ($serviceRegistered) {
    Write-Host "  系统服务: 已注册 (开机自启)"
} else {
    Write-Host "  系统服务: 未注册 (使用启停脚本)"
}
Write-Host ""
Write-Host "  卸载: $installDir\uninstall.ps1"
Write-Host ""
```

- [ ] **Step 4: 提交**

```bash
git add installer/install.ps1
git commit -m "feat: add Windows installer TUI with 6-step guided setup"
```

---

### Task 7: Windows 卸载脚本

**Files:**
- Create: `installer/uninstall.ps1`

- [ ] **Step 1: 创建 uninstall.ps1**

```powershell
# Ascoder 卸载脚本 (Windows)
param(
    [string]$InstallDir = ""
)
$ErrorActionPreference = "Stop"

if (-not $InstallDir) {
    if (Test-Path "C:\Ascoder\ascoder.jar") {
        $InstallDir = "C:\Ascoder"
    } else {
        Write-Host "请指定安装目录: .\uninstall.ps1 -InstallDir <path>" -ForegroundColor Red
        exit 1
    }
}

Write-Host "Ascoder 卸载" -ForegroundColor Cyan
Write-Host ""
Write-Host "  将删除以下内容："
Write-Host "    - 安装目录: $InstallDir"
Write-Host "    - 系统服务注册"
Write-Host ""
Write-Host "警告: 此操作不可恢复！所有数据（仓库、索引）将被删除。" -ForegroundColor Red
Write-Host ""

$confirm = Read-Host "确认卸载? 输入 YES 继续"
if ($confirm -ne "YES") {
    Write-Host "卸载已取消。"
    exit 0
}

# 停止服务
$stopScript = Join-Path $InstallDir "bin\stop.ps1"
if (Test-Path $stopScript) {
    Write-Host "正在停止 Ascoder ..."
    & $stopScript 2>$null
}

# 注销 Windows 服务
try {
    $service = Get-Service -Name "Ascoder" -ErrorAction SilentlyContinue
    if ($service) {
        Write-Host "正在注销 Windows 服务 ..."
        & sc.exe stop Ascoder 2>$null
        & sc.exe delete Ascoder 2>$null
    }
} catch {
    # 忽略
}

# 删除安装目录
Write-Host "正在删除安装目录 $InstallDir ..."
Remove-Item -Path $InstallDir -Recurse -Force

Write-Host ""
Write-Host "Ascoder 已完全卸载。"
```

- [ ] **Step 2: 提交**

```bash
git add installer/uninstall.ps1
git commit -m "feat: add Windows uninstall script"
```

---

### Task 8: 自动打包工具

**Files:**
- Create: `scripts/package.sh`

- [ ] **Step 1: 创建 package.sh**

```bash
#!/usr/bin/env bash
# Ascoder 自动打包工具
# 在开发机上执行，构建并打包为可分发 zip
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
STAGING_DIR="$ROOT_DIR/build/staging"
OUTPUT_DIR="$ROOT_DIR/build/output"

echo "=========================================="
echo "  Ascoder 自动打包工具"
echo "=========================================="
echo ""

# --- 读取版本号 ---
VERSION=$(grep '<version>' "$ROOT_DIR/pom.xml" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
ZIP_NAME="ascoder-${VERSION}.zip"
echo "版本: $VERSION"
echo ""

# --- [1] 检测构建工具 ---
echo "[1/7] 检测构建工具 ..."
if ! command -v mvn >/dev/null 2>&1 && [ ! -f "$ROOT_DIR/backend/mvnw" ]; then
  echo "ERROR: Maven not found. Install Maven or use ./mvnw" >&2
  exit 1
fi

MVN="mvn"
if [ -f "$ROOT_DIR/mvnw" ]; then
  MVN="$ROOT_DIR/mvnw"
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "ERROR: npm not found. Install Node.js first." >&2
  exit 1
fi

echo "  Maven: $MVN"
echo "  npm: $(which npm)"
echo ""

# --- [2] 构建 JAR ---
echo "[2/7] 构建 JAR ..."
(cd "$ROOT_DIR" && $MVN -pl backend -am package -DskipTests -q)
JAR_FILE="$ROOT_DIR/backend/target/ascoder-backend-${VERSION}.jar"
if [ ! -f "$JAR_FILE" ]; then
  echo "ERROR: JAR not found at $JAR_FILE" >&2
  exit 1
fi
echo "  JAR: $JAR_FILE"
echo ""

# --- [3] 构建前端 ---
echo "[3/7] 构建前端 ..."
(cd "$ROOT_DIR/frontend/web" && npm install --quiet && npm run build)
DIST_DIR="$ROOT_DIR/frontend/web/dist"
if [ ! -d "$DIST_DIR" ]; then
  echo "ERROR: Frontend dist not found at $DIST_DIR" >&2
  exit 1
fi
echo "  Dist: $DIST_DIR"
echo ""

# --- [4] 检查 CodeGraph CLI 二进制 ---
echo "[4/7] 检查 CodeGraph CLI 二进制 ..."
CG_DIR="$ROOT_DIR/installer/lib/codegraph"
CG_MISSING=false
for bin in codegraph-linux-amd64 codegraph-darwin-amd64 codegraph-darwin-arm64 codegraph-windows-amd64.exe; do
  if [ ! -f "$CG_DIR/$bin" ]; then
    echo "  MISSING: $CG_DIR/$bin"
    CG_MISSING=true
  else
    echo "  OK: $bin"
  fi
done

if [ "$CG_MISSING" = "true" ]; then
  echo ""
  echo "WARNING: 部分 CodeGraph CLI 二进制缺失。"
  echo "  请从 https://github.com/colbymchenry/codegraph/releases 下载对应平台二进制"
  echo "  放置到: $CG_DIR/"
  echo ""
  read -r -p "忽略缺失继续打包? [y/N]: " skip
  if [ "$skip" != "y" ] && [ "$skip" != "Y" ]; then
    echo "打包中止。"
    exit 1
  fi
fi
echo ""

# --- [5] 组装临时目录 ---
echo "[5/7] 组装打包目录 ..."
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR/ascoder"

# 复制安装脚本
cp "$ROOT_DIR/installer/install.sh" "$STAGING_DIR/ascoder/"
cp "$ROOT_DIR/installer/install.ps1" "$STAGING_DIR/ascoder/"
cp "$ROOT_DIR/installer/uninstall.sh" "$STAGING_DIR/ascoder/"
cp "$ROOT_DIR/installer/uninstall.ps1" "$STAGING_DIR/ascoder/"

# 复制 JAR（重命名为 ascoder.jar）
cp "$JAR_FILE" "$STAGING_DIR/ascoder/ascoder.jar"

# 复制前端
cp -r "$DIST_DIR" "$STAGING_DIR/ascoder/frontend/dist"

# 复制启停脚本
mkdir -p "$STAGING_DIR/ascoder/bin"
cp "$ROOT_DIR/installer/bin/start.sh" "$STAGING_DIR/ascoder/bin/"
cp "$ROOT_DIR/installer/bin/stop.sh" "$STAGING_DIR/ascoder/bin/"
cp "$ROOT_DIR/installer/bin/start.ps1" "$STAGING_DIR/ascoder/bin/"
cp "$ROOT_DIR/installer/bin/stop.ps1" "$STAGING_DIR/ascoder/bin/"

# 复制 CodeGraph 二进制
if [ -d "$CG_DIR" ]; then
  mkdir -p "$STAGING_DIR/ascoder/lib/codegraph"
  cp "$CG_DIR"/* "$STAGING_DIR/ascoder/lib/codegraph/" 2>/dev/null || true
fi

# 复制配置模板
mkdir -p "$STAGING_DIR/ascoder/conf"
cp "$ROOT_DIR/installer/conf/application.yml" "$STAGING_DIR/ascoder/conf/"

# 复制 README
cp "$ROOT_DIR/installer/README.txt" "$STAGING_DIR/ascoder/"

echo "  组装完成。"
echo ""

# --- [6] 打包 ---
echo "[6/7] 打包 zip ..."
mkdir -p "$OUTPUT_DIR"
rm -f "$OUTPUT_DIR/$ZIP_NAME"
(cd "$STAGING_DIR" && zip -r -q "$OUTPUT_DIR/$ZIP_NAME" ascoder/)

ZIP_SIZE=$(du -h "$OUTPUT_DIR/$ZIP_NAME" | cut -f1)
echo "  输出: $OUTPUT_DIR/$ZIP_NAME ($ZIP_SIZE)"
echo ""

# --- [7] 清理 ---
echo "[7/7] 清理临时文件 ..."
rm -rf "$STAGING_DIR"
echo ""

echo "=========================================="
echo "  ✓ 打包完成！"
echo "=========================================="
echo ""
echo "  文件: $OUTPUT_DIR/$ZIP_NAME ($ZIP_SIZE)"
echo "  安装: 解压后运行 install.sh (Unix) 或 install.ps1 (Windows)"
echo ""
```

- [ ] **Step 2: 设置可执行权限**

```bash
chmod +x scripts/package.sh
```

- [ ] **Step 3: 更新 .gitignore**

在 `.gitignore` 中添加：

```
# Build output
build/
```

- [ ] **Step 4: 提交**

```bash
git add scripts/package.sh .gitignore
git commit -m "feat: add auto-packaging tool for distributable zip"
```

---

## 自审检查

### 1. Spec 覆盖

| Spec 要求 | Task |
|-----------|------|
| zip 包结构 | Task 1-8 覆盖所有文件 |
| [1/6] 欢迎确认 | Task 4 (Unix), Task 6 (Windows) |
| [2/6] 选择安装目录 | Task 4, Task 6 |
| [3/6] 环境检测 & 依赖安装 | Task 4, Task 6 |
| [4/6] 数据库配置（远程 MySQL） | Task 4, Task 6 |
| [5/6] LLM 配置 | Task 4, Task 6 |
| [6/6] 生成配置 & 服务注册 | Task 4, Task 6 |
| Unix 启停脚本 | Task 1 |
| Windows 启停脚本 | Task 2 |
| systemd 注册 | Task 4 |
| launchd 注册 | Task 4 |
| Windows 服务 (sc.exe) | Task 6 |
| 卸载脚本 (Unix) | Task 5 |
| 卸载脚本 (Windows) | Task 7 |
| 自动打包工具 | Task 8 |
| 配置模板 | Task 3 |
| README.txt | Task 3 |

无遗漏。

### 2. 占位符扫描

无 TBD/TODO/占位符，所有步骤包含完整代码。

### 3. 类型一致性

- JAR 文件名：所有 Task 统一使用 `ascoder.jar`（打包时从 `ascoder-backend-0.1.0-SNAPSHOT.jar` 重命名）
- 环境变量名：`.env` 中 `SPRING_DATASOURCE_URL` 等与 `application.yml` 模板中的 `${SPRING_DATASOURCE_URL}` 一致
- CodeGraph 可执行文件路径：Unix 用 `$INSTALL_DIR/bin/codegraph`，Windows 用 `Join-Path $installDir "bin\codegraph.exe"`，与 `application.yml` 模板 `${CODEGRAPH_EXECUTABLE}` 对应

全部一致，无问题。
