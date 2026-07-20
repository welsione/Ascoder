#!/usr/bin/env bash
# Ascoder 全链路 Docker 验证脚本
# 用法: ./scripts/verify-docker.sh [OPTIONS]
#   --tarball FILE   预加载镜像 tarball（不传则走 docker compose pull）
#   --repo URL       测试仓库 URL（默认 octocat/Hello-World）
#   --port PORT      前端端口（默认 5173）
#   --skip-llm       跳过 LLM 问答测试（无需 API Key）
set -euo pipefail

# 默认参数
IMAGE_TARBALL=""
REPO_URL="https://github.com/octocat/Hello-World"
APP_PORT="5173"
SKIP_LLM=false

# 解析参数
while [ $# -gt 0 ]; do
  case "$1" in
    --tarball) IMAGE_TARBALL="$2"; shift 2 ;;
    --repo) REPO_URL="$2"; shift 2 ;;
    --port) APP_PORT="$2"; shift 2 ;;
    --skip-llm) SKIP_LLM=true; shift ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

step_ok()   { echo -e "  ${GREEN}✓${NC} $1"; }
step_fail() { echo -e "  ${RED}✗${NC} $1"; }
step_skip() { echo -e "  ${YELLOW}⊘${NC} $1"; }
step_info() { echo -e "  ${YELLOW}…${NC} $1"; }

ERRORS=0

# ============================================================
# Step 1: Load images or pull
# ============================================================
echo ""
echo "[1/9] Docker 镜像准备"
if [ -n "$IMAGE_TARBALL" ]; then
  step_info "从 tarball 加载镜像: $IMAGE_TARBALL"
  if docker load < "$IMAGE_TARBALL" >/dev/null 2>&1; then
    step_ok "镜像加载成功"
  else
    step_fail "镜像加载失败"; ERRORS=$((ERRORS + 1))
  fi
else
  step_info "拉取镜像 (docker compose pull) ..."
  if docker compose -f "$COMPOSE_FILE" pull 2>/dev/null; then
    step_ok "镜像拉取成功"
  else
    step_fail "镜像拉取失败（可能需要配置代理或预加载 tarball）"; ERRORS=$((ERRORS + 1))
  fi
fi

# ============================================================
# Step 2: Start services
# ============================================================
echo ""
echo "[2/9] 启动服务"
if docker compose -f "$COMPOSE_FILE" up -d --build 2>/dev/null; then
  step_ok "docker compose up -d 成功"
else
  step_fail "docker compose up -d 失败"; ERRORS=$((ERRORS + 1))
fi

# ============================================================
# Step 3: MySQL healthy
# ============================================================
echo ""
echo "[3/9] MySQL 健康检查"
_mysql_ok=false
for _i in $(seq 1 15); do
  if docker compose -f "$COMPOSE_FILE" ps mysql 2>/dev/null | grep -q "healthy"; then
    _mysql_ok=true; break
  fi
  sleep 2
done
if [ "$_mysql_ok" = true ]; then
  step_ok "MySQL healthy"
else
  step_fail "MySQL 未在 30s 内变为 healthy"; ERRORS=$((ERRORS + 1))
fi

# ============================================================
# Step 4: Backend healthy
# ============================================================
echo ""
echo "[4/9] 后端健康检查"
_backend_ok=false
for _i in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:18080/api/health" >/dev/null 2>&1; then
    _backend_ok=true; break
  fi
  sleep 2
done
if [ "$_backend_ok" = true ]; then
  step_ok "Backend healthy"
else
  step_fail "Backend 未在 60s 内变为 healthy"; ERRORS=$((ERRORS + 1))
fi

# ============================================================
# Step 5: Frontend reachable
# ============================================================
echo ""
echo "[5/9] 前端可达"
if curl -fsS "http://127.0.0.1:${APP_PORT}/" >/dev/null 2>&1; then
  step_ok "Frontend 200 OK"
else
  step_fail "Frontend 不可达"; ERRORS=$((ERRORS + 1))
fi

# ============================================================
# Step 6: Backend connected to MySQL
# ============================================================
echo ""
echo "[6/9] 后端连接 MySQL"
if docker compose -f "$COMPOSE_FILE" logs backend 2>/dev/null | grep -qi "started"; then
  step_ok "后端启动日志正常"
else
  step_fail "后端启动日志未出现 'started'"; ERRORS=$((ERRORS + 1))
fi

# ============================================================
# Step 7: Git clone via API
# ============================================================
echo ""
echo "[7/9] Git clone（注册仓库）"
_clone_result=""
_clone_result=$(curl -sS -X POST "http://127.0.0.1:18080/api/repositories" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Hello-World\",\"remoteUrl\":\"${REPO_URL}\"}" 2>&1) || true
if echo "$_clone_result" | grep -qi "ready\|cloning\|id"; then
  step_ok "仓库注册成功"
  # 提取仓库 ID
  REPO_ID=$(echo "$_clone_result" | grep -oE '"id":[0-9]+' | grep -oE '[0-9]+' | head -1 || true)
else
  step_fail "仓库注册失败: $_clone_result"; ERRORS=$((ERRORS + 1))
  REPO_ID=""
fi

# ============================================================
# Step 8: CodeGraph index
# ============================================================
echo ""
echo "[8/9] CodeGraph 索引"
if [ -n "$REPO_ID" ]; then
  step_info "触发索引 (repository id=$REPO_ID) ..."
  curl -sS -X POST "http://127.0.0.1:18080/api/repositories/${REPO_ID}/index" >/dev/null 2>&1 || true
  _index_ok=false
  for _i in $(seq 1 90); do
    _status=$(curl -sS "http://127.0.0.1:18080/api/repositories/${REPO_ID}" 2>/dev/null | grep -oE '"status":"[a-z]+"' | head -1 || true)
    if echo "$_status" | grep -qi "ready"; then
      _index_ok=true; break
    fi
    sleep 20
  done
  if [ "$_index_ok" = true ]; then
    step_ok "索引完成 (status=ready)"
  else
    step_fail "索引未在 1800s 内完成 (last status: $_status)"; ERRORS=$((ERRORS + 1))
  fi
else
  step_skip "索引跳过（仓库注册失败）"
fi

# ============================================================
# Step 9: LLM Q&A (optional)
# ============================================================
echo ""
echo "[9/9] LLM 问答测试"

# Check if at least one LLM key is set
_has_llm_key=false
if [ -n "${MINIMAX_API_KEY:-}" ] || [ -n "${ANTHROPIC_API_KEY:-}" ] || [ -n "${OPENAI_API_KEY:-}" ]; then
  _has_llm_key=true
fi

if [ "$SKIP_LLM" = true ]; then
  step_skip "LLM 问答跳过（--skip-llm 标志）"
elif [ "$_has_llm_key" = false ]; then
  step_skip "LLM 问答跳过（未配置 LLM API Key）"
else
  step_info "发送测试问题 ..."
  # 创建对话
  _conv_result=$(curl -sS -X POST "http://127.0.0.1:18080/api/conversations" \
    -H "Content-Type: application/json" \
    -d '{"question":"这个仓库的目录结构是什么？"}' 2>&1) || true
  if echo "$_conv_result" | grep -qi "id\|data:"; then
    step_ok "SSE 流首帧返回"
  else
    step_fail "SSE 流未返回: $_conv_result"; ERRORS=$((ERRORS + 1))
  fi
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "========================================"
if [ "$ERRORS" -eq 0 ]; then
  echo -e "${GREEN}全部通过 ✓${NC} (errors=0)"
  exit 0
else
  echo -e "${RED}存在失败 ✗${NC} (errors=$ERRORS)"
  echo ""
  echo "排错建议："
  echo "  docker compose -f $COMPOSE_FILE logs backend"
  echo "  docker compose -f $COMPOSE_FILE logs mysql"
  exit 1
fi
