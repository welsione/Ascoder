#!/usr/bin/env bash
# Ascoder 安装引导脚本 (Docker Compose)
set -euo pipefail

VERSION="0.1.0"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 工具函数
info()  { echo "[INFO] $*"; }
ok()    { echo "[OK] $*"; }
warn()  { echo "[WARN] $*"; }
error() { echo "[ERROR] $*"; }

prompt() {
  local var="$1" message="$2" default="${3:-}"
  if [ -n "$default" ]; then
    # shellcheck disable=SC2229
    read -r -p "$message [$default]: " "$var"
    eval "$var=\${$var:-$default}"
  else
    # shellcheck disable=SC2229
    read -r -p "$message: " "$var"
  fi
}

confirm() {
  local message="$1" answer
  read -r -p "$message [Y/n]: " answer
  case "$answer" in
    n|N) return 1 ;;
    *) return 0 ;;
  esac
}

# ============================================================
# [1/6] 欢迎
# ============================================================
echo ""
echo "Ascoder v${VERSION} - 团队代码理解平台"
echo ""
echo "本向导将引导您完成以下步骤："
echo "  1. 选择安装目录"
echo "  2. 检测 Docker 环境"
echo "  3. 配置 MySQL 数据库连接"
echo "  4. 配置 LLM API"
echo "  5. 生成配置并启动"
echo ""

if ! confirm "是否开始安装?"; then
  echo "安装已取消。"
  exit 0
fi

# ============================================================
# [2/6] 安装目录
# ============================================================
DEFAULT_INSTALL_DIR="$HOME/ascoder"
prompt INSTALL_DIR "请输入安装目录" "$DEFAULT_INSTALL_DIR"
INSTALL_DIR="${INSTALL_DIR/#\~/$HOME}"

if [ -d "$INSTALL_DIR" ] && [ -f "$INSTALL_DIR/.env" ]; then
  warn "目录 $INSTALL_DIR 已存在且包含 .env 配置。"
  if confirm "是否覆盖安装?"; then
    info "将复用已有配置。"
  else
    echo "安装已取消。"
    exit 0
  fi
else
  mkdir -p "$INSTALL_DIR"
fi

# 复制编排文件到安装目录
info "正在复制编排文件到 $INSTALL_DIR ..."
cp "$SCRIPT_DIR/docker-compose.yml" "$INSTALL_DIR/" 2>/dev/null || true
cp "$SCRIPT_DIR/docker-compose.ssh.yml" "$INSTALL_DIR/" 2>/dev/null || true
cp "$SCRIPT_DIR/docker-compose.host-repos.yml" "$INSTALL_DIR/" 2>/dev/null || true
cp "$SCRIPT_DIR/.env.example" "$INSTALL_DIR/.env.example" 2>/dev/null || true
cp -r "$SCRIPT_DIR/installer/bin" "$INSTALL_DIR/bin" 2>/dev/null || true
cp -r "$SCRIPT_DIR/backend" "$INSTALL_DIR/backend" 2>/dev/null || true
cp -r "$SCRIPT_DIR/frontend" "$INSTALL_DIR/frontend" 2>/dev/null || true
ok "编排文件复制完成。"

# ============================================================
# [3/6] Docker 环境检测
# ============================================================
echo ""
info "检测 Docker 环境 ..."

if ! command -v docker >/dev/null 2>&1; then
  error "未检测到 Docker。请先安装 Docker："
  error "  macOS:  https://docs.docker.com/desktop/install/mac-install/"
  error "  Linux:  sudo apt install docker.io docker-compose-plugin"
  error "  Windows: https://docs.docker.com/desktop/install/windows-install/"
  echo ""
  read -r -p "安装完成后按回车继续..."
  if ! command -v docker >/dev/null 2>&1; then
    error "Docker 仍不可用，安装中止。"
    exit 1
  fi
fi

if ! docker info >/dev/null 2>&1; then
  error "Docker daemon 未运行。请先启动 Docker Desktop 或 Docker 服务。"
  read -r -p "启动后按回车继续..."
  if ! docker info >/dev/null 2>&1; then
    error "Docker daemon 仍不可用，安装中止。"
    exit 1
  fi
fi

if ! docker compose version >/dev/null 2>&1; then
  error "未检测到 Docker Compose v2 插件。请安装 docker-compose-plugin。"
  exit 1
fi

ok "Docker 环境正常 ($(docker --version), Compose $(docker compose version --short))"

# ============================================================
# [4/6] MySQL 配置
# ============================================================
echo ""
info "MySQL 配置"
echo "  默认使用容器内 MySQL（推荐）。如需连接自建 MySQL，请选择自定义。"
echo ""

USE_CONTAINER_MYSQL="Y"
prompt USE_CONTAINER_MYSQL "使用容器内 MySQL? [Y/n]" "Y"

if echo "$USE_CONTAINER_MYSQL" | grep -qi '^n'; then
  prompt DB_HOST "MySQL 主机地址" "127.0.0.1"
  prompt DB_PORT "MySQL 端口" "3306"
  prompt DB_USER "MySQL 用户名" ""
  prompt DB_PASS "MySQL 密码" ""
  prompt DB_NAME "数据库名" "ascoder"
  SPRING_DATASOURCE_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
  SPRING_DATASOURCE_USERNAME="$DB_USER"
  SPRING_DATASOURCE_PASSWORD="$DB_PASS"
else
  SPRING_DATASOURCE_URL=""
  SPRING_DATASOURCE_USERNAME=""
  SPRING_DATASOURCE_PASSWORD=""
fi

# ============================================================
# [5/6] LLM 配置
# ============================================================
echo ""
info "LLM API 配置"
echo "  选择 LLM 服务商："
echo "    1) MiniMax"
echo "    2) Anthropic"
echo "    3) OpenAI"
echo ""

LLM_CHOICE=""
while [ -z "$LLM_CHOICE" ] || ! echo "$LLM_CHOICE" | grep -qE '^[1-3]$'; do
  prompt LLM_CHOICE "请选择 (1-3)" ""
done

case "$LLM_CHOICE" in
  1) LLM_PROVIDER="agentscope"; DEFAULT_MODEL="MiniMax-M2.7"; DEFAULT_BASE_URL="https://api.minimaxi.com/anthropic"; API_KEY_VAR="MINIMAX_API_KEY" ;;
  2) LLM_PROVIDER="anthropic"; DEFAULT_MODEL="claude-sonnet-4-6-20250514"; DEFAULT_BASE_URL="https://api.anthropic.com"; API_KEY_VAR="ANTHROPIC_API_KEY" ;;
  3) LLM_PROVIDER="openai"; DEFAULT_MODEL="gpt-4o"; DEFAULT_BASE_URL="https://api.openai.com/v1"; API_KEY_VAR="OPENAI_API_KEY" ;;
esac

prompt API_KEY "API Key (${API_KEY_VAR})" ""
prompt AGENT_MODEL_ID "Model ID" "$DEFAULT_MODEL"
prompt AGENT_BASE_URL "Base URL" "$DEFAULT_BASE_URL"

# 询问 GIT_TOKEN（可选）
echo ""
prompt GIT_TOKEN_INPUT "Git HTTPS Token (可选, 私有仓库需要)" ""
prompt GIT_USERNAME_INPUT "Git Username (默认 git)" "git"
prompt GIT_EXTRA_HOSTS_INPUT "Git Extra Hosts (可选, 逗号分隔, 如 git.example.com)" ""

ok "配置收集完成。"

# ============================================================
# [6/6] 生成配置并启动
# ============================================================
echo ""
info "正在生成配置文件 $INSTALL_DIR/.env ..."

# 读取已有 .env（如有）或从 .env.example 复制
if [ ! -f "$INSTALL_DIR/.env" ]; then
  cp "$INSTALL_DIR/.env.example" "$INSTALL_DIR/.env" 2>/dev/null || touch "$INSTALL_DIR/.env"
fi

# 写入用户配置
write_env() {
  local key="$1" value="$2"
  if grep -q "^${key}=" "$INSTALL_DIR/.env" 2>/dev/null; then
    sed -i.bak "s|^${key}=.*|${key}=${value}|" "$INSTALL_DIR/.env" && rm -f "$INSTALL_DIR/.env.bak"
  else
    echo "${key}=${value}" >> "$INSTALL_DIR/.env"
  fi
}

write_env "APP_PORT" "5173"
write_env "LLM_PROVIDER" "$LLM_PROVIDER"
write_env "AGENT_MODEL_ID" "$AGENT_MODEL_ID"
write_env "AGENT_BASE_URL" "$AGENT_BASE_URL"
write_env "MINIMAX_API_KEY" "$([ "$API_KEY_VAR" = "MINIMAX_API_KEY" ] && echo "$API_KEY" || echo "")"
write_env "ANTHROPIC_API_KEY" "$([ "$API_KEY_VAR" = "ANTHROPIC_API_KEY" ] && echo "$API_KEY" || echo "")"
write_env "OPENAI_API_KEY" "$([ "$API_KEY_VAR" = "OPENAI_API_KEY" ] && echo "$API_KEY" || echo "")"

if [ -n "$SPRING_DATASOURCE_URL" ]; then
  write_env "SPRING_DATASOURCE_URL" "$SPRING_DATASOURCE_URL"
  write_env "SPRING_DATASOURCE_USERNAME" "$SPRING_DATASOURCE_USERNAME"
  write_env "SPRING_DATASOURCE_PASSWORD" "$SPRING_DATASOURCE_PASSWORD"
fi

if [ -n "$GIT_TOKEN_INPUT" ]; then
  write_env "GIT_TOKEN" "$GIT_TOKEN_INPUT"
  write_env "GIT_USERNAME" "$GIT_USERNAME_INPUT"
fi

if [ -n "$GIT_EXTRA_HOSTS_INPUT" ]; then
  write_env "GIT_EXTRA_HOSTS" "$GIT_EXTRA_HOSTS_INPUT"
fi

chmod 600 "$INSTALL_DIR/.env"
ok "配置文件已生成。"

# 启动
info "正在启动 Ascoder ..."
cd "$INSTALL_DIR"
docker compose up -d --build

# 等待后端健康
info "等待后端启动 ..."
for _i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:18080/api/health >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo ""
ok "Ascoder 安装完成！"
echo "  访问地址: http://localhost:5173"
echo "  配置文件: $INSTALL_DIR/.env"
echo "  启停命令: $INSTALL_DIR/bin/start.sh | $INSTALL_DIR/bin/stop.sh"
echo "  卸载:     $INSTALL_DIR/uninstall.sh"
