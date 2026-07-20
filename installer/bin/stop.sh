#!/usr/bin/env bash
# Ascoder 停止脚本 (Docker Compose)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "Stopping Ascoder..."
docker compose -f "$INSTALL_DIR/docker-compose.yml" down
echo "Ascoder stopped."
