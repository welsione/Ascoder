#!/usr/bin/env bash
# Ascoder 卸载脚本 (Docker Compose)
set -euo pipefail

RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m'

INSTALL_DIR="${1:-}"

if [ -z "$INSTALL_DIR" ]; then
  # 尝试从常见位置查找
  if [ -f "$HOME/ascoder/docker-compose.yml" ]; then
    INSTALL_DIR="$HOME/ascoder"
  elif [ -f "/opt/ascoder/docker-compose.yml" ]; then
    INSTALL_DIR="/opt/ascoder"
  else
    echo -e "${RED}请指定安装目录: $0 <install-dir>${NC}"
    exit 1
  fi
fi

echo -e "${BOLD}Ascoder 卸载${NC}"
echo ""
echo "  将删除以下内容："
echo "    - 安装目录: $INSTALL_DIR"
echo "    - Docker 容器、网络、数据卷"
echo ""
echo -e "${RED}警告: 此操作不可恢复！所有数据（仓库、索引）将被删除。${NC}"
echo ""

read -r -p "$(echo -e "${BOLD}确认卸载? 输入 YES 继续:${NC} ")" confirm
if [ "$confirm" != "YES" ]; then
  echo "卸载已取消。"
  exit 0
fi

# 停止服务并清理 Docker 资源
if [ -f "$INSTALL_DIR/docker-compose.yml" ]; then
  echo "正在停止并清理 Docker 资源 ..."
  docker compose -f "$INSTALL_DIR/docker-compose.yml" down -v 2>/dev/null || true
fi

# 删除安装目录
echo "正在删除安装目录 $INSTALL_DIR ..."
rm -rf "$INSTALL_DIR"

echo ""
echo "Ascoder 已完全卸载。"
