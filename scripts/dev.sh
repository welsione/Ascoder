#!/usr/bin/env bash
# Ascoder 测试环境一键脚本。所有命令需在仓库根目录或任意目录执行均可(脚本自定位)。
#
# 测试数据库:docker 化 MySQL 8.4,宿主机端口 3307,数据持久化到 ./data/mysql-test。
# 两种模式:dev-local(本地跑后端/前端,容器跑 DB)、dev-docker(全套容器)。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.test.yml"
MYSQL_DATA="$PROJECT_ROOT/data/mysql-test"

cd "$PROJECT_ROOT"

MODE="${1:-}"
ACTION="${2:-}"

usage() {
  cat <<'EOF'
用法:
  bash scripts/dev.sh local up       起测试数据库(3307),后端/前端本地跑
  bash scripts/dev.sh local down     停数据库(保留数据)
  bash scripts/dev.sh local reset    停数据库并清空数据(Flyway 重建表)

  bash scripts/dev.sh docker up      全套容器(backend + frontend + mysql)
  bash scripts/dev.sh docker down    停全部(保留数据)
  bash scripts/dev.sh docker reset   停全部并清空数据
  bash scripts/dev.sh docker logs    查看后端日志
EOF
  exit 1
}

[ -z "$MODE" ] || [ -z "$ACTION" ] && usage

# 测试库用 bind mount(./data/mysql-test),down -v 不会清空,需手动删目录。
clear_mysql_data() {
  if [ -d "$MYSQL_DATA" ]; then
    rm -rf "$MYSQL_DATA"
    echo "cleared $MYSQL_DATA"
  fi
}

case "$MODE" in
  local)
    case "$ACTION" in
      up)
        docker compose -f "$COMPOSE_FILE" up -d mysql
        echo ""
        echo "✓ 测试数据库已就绪(127.0.0.1:3307)。后端/前端本地启动:"
        echo "    mvn spring-boot:run -pl backend -Dspring-boot.run.profiles=dev-local"
        echo "    cd frontend/web && npm run dev"
        ;;
      down)
        docker compose -f "$COMPOSE_FILE" down
        ;;
      reset)
        docker compose -f "$COMPOSE_FILE" down
        clear_mysql_data
        echo "✓ 测试数据已清空。重新 up 后 Flyway 会重建表。"
        ;;
      *) echo "unknown action: $ACTION"; usage ;;
    esac
    ;;
  docker)
    # backend/Dockerfile COPY 预构建 jar,docker build 前需先 mvn package。
    build_jar() {
      echo "building backend jar (mvn package)..."
      mvn -q -pl backend -am package -DskipTests
    }
    case "$ACTION" in
      up)
        build_jar
        docker compose -f "$COMPOSE_FILE" --profile docker up -d --build
        echo ""
        echo "✓ 全套容器已启动。"
        echo "    前端: http://localhost:${APP_PORT:-5173}"
        echo "    后端: http://localhost:${BACKEND_PORT:-18080}/api/health"
        ;;
      down)
        docker compose -f "$COMPOSE_FILE" --profile docker down
        ;;
      reset)
        docker compose -f "$COMPOSE_FILE" --profile docker down
        clear_mysql_data
        echo "✓ 测试数据已清空。重新 up 后 Flyway 会重建表。"
        ;;
      logs)
        docker compose -f "$COMPOSE_FILE" logs -f backend
        ;;
      *) echo "unknown action: $ACTION"; usage ;;
    esac
    ;;
  *) echo "unknown mode: $MODE"; usage ;;
esac
