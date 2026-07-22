#!/usr/bin/env bash
# Ascoder 局域网服务器自动更新脚本（由 cron 定时调用）。
#
# 设计约束：服务器在局域网内，只能主动从 GitHub 拉取，GitHub 无法反向访问服务器。
# 因此部署完全基于"拉取"：拉最新 compose 文件 + 拉最新镜像 + 重启容器。
#
# 用法（通常由 cron 触发，也可手动执行）：
#   bash scripts/server/deploy.sh
#
# 日志：写入同目录上级的 deploy.log。
set -euo pipefail

# 部署根目录 = 脚本所在目录上两级（scripts/server/ -> 仓库根）
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$DEPLOY_DIR"

COMPOSE_FILE="docker-compose.prod.yml"
LOG_FILE="${DEPLOY_DIR}/deploy.log"

log() {
    printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG_FILE"
}

# 1. 拉取最新的 compose 与脚本（源码已打进镜像，仓库在这里仅用于同步部署文件）
# 容错策略：服务器到 GitHub 经代理（gh-proxy.com），网络易抖动，fetch 重试 3 次、每次 30s。
# fetch 或 checkout 任一失败即中止部署：镜像走 GHCR CDN 通常能拉到新版，若此时用旧 compose
# 启动新镜像，会造成配置不一致（如 volume 挂载缺失、env 变化），比"暂不更新"更危险。
# 因此宁可保持旧的镜像 + 旧 compose 一致状态，也不允许半更新。
log "fetching latest deployment files from origin/master..."
fetch_ok=0
for attempt in 1 2 3; do
    if timeout 30 git fetch origin master --depth=1 --force --quiet >>"$LOG_FILE" 2>&1; then
        fetch_ok=1
        break
    fi
    [ "$attempt" -lt 3 ] && log "WARN: git fetch attempt $attempt failed, retrying in 2s..."
    sleep 2
done
if [ "$fetch_ok" != "1" ]; then
    log "ERROR: git fetch failed after 3 attempts. Aborting to avoid config mismatch (new image with stale compose)."
    exit 1
fi
# 只 checkout 部署相关文件，不触碰 .env / data/（它们未跟踪，见 .gitignore）。
# 不吞错：checkout 失败（如索引锁定、路径冲突）时 set -e 触发退出，避免用旧 compose 跑新镜像。
git checkout origin/master -- "$COMPOSE_FILE" .env.example scripts/ >>"$LOG_FILE" 2>&1
log "deployment files synced."

# 1.5 自动生成 ASCODER_ENCRYPTION_KEY（首次部署）
# 非开发环境必须配置加密密钥（ApiKeyEncryptor 在非 dev profile 下不允许默认密钥），
# 未配置会导致配置 LLM provider 时 encrypt() 抛异常 -> 409。
# 首次部署自动生成并持久化到 .env；后续保留已生成的密钥（变更后已加密数据无法解密）。
# 如需自定义密钥，在 .env 手动设置 ASCODER_ENCRYPTION_KEY 即可，脚本不会覆盖。
if [ -f .env ] && ! grep -q "^ASCODER_ENCRYPTION_KEY=." .env 2>/dev/null; then
    KEY=$(openssl rand -base64 32)
    if grep -q "^ASCODER_ENCRYPTION_KEY=" .env 2>/dev/null; then
        awk -v k="$KEY" '/^ASCODER_ENCRYPTION_KEY=/{print "ASCODER_ENCRYPTION_KEY=" k; next} 1' .env > .env.tmp && mv .env.tmp .env
    else
        echo "ASCODER_ENCRYPTION_KEY=$KEY" >> .env
    fi
    log "generated ASCODER_ENCRYPTION_KEY into .env (auto)"
fi

# 2. 拉取最新镜像
log "pulling images..."
if ! docker compose -f "$COMPOSE_FILE" pull 2>&1 | tee -a "$LOG_FILE"; then
    log "ERROR: image pull failed (check GHCR login if repo is private)"
    exit 1
fi

# 3. 重启（镜像未变时 compose 不会重启容器，避免无谓抖动）
log "recreating containers if image changed..."
docker compose -f "$COMPOSE_FILE" up -d 2>&1 | tee -a "$LOG_FILE"

# 4. 清理悬挂的旧镜像
docker image prune -f >/dev/null 2>&1 || true

log "update done."
