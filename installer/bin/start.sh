#!/usr/bin/env bash
# Ascoder 启动脚本 (Docker Compose)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$INSTALL_DIR/.env"

# 加载 .env
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

APP_PORT="${APP_PORT:-5173}"

echo "Starting Ascoder..."
docker compose -f "$INSTALL_DIR/docker-compose.yml" up -d

# 等待后端健康
echo "Waiting for backend to become healthy..."
for _i in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:18080/api/health" >/dev/null 2>&1; then
    echo "Ascoder started successfully."
    echo "  Backend:  http://localhost:18080"
    echo "  Frontend: http://localhost:${APP_PORT}"
    exit 0
  fi
  sleep 2
done

echo "Warning: backend may still be starting. Check logs:"
echo "  docker compose -f $INSTALL_DIR/docker-compose.yml logs backend"
