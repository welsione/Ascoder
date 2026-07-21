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
# 容错：服务器到 GitHub 的网络可能不稳定（尤其老版 git），fetch 超时或失败时跳过文件更新，
# 继续走 GHCR 拉镜像--compose 结构低频变化，旧版本通常也能跑；镜像更新走 GHCR CDN，不受 git 网络影响。
log "fetching latest deployment files from origin/master..."
if timeout 15 git fetch origin master --depth=1 --quiet >>"$LOG_FILE" 2>&1; then
    # 只 checkout 部署相关文件，不触碰 .env / data/（它们未跟踪，见 .gitignore）
    git checkout origin/master -- "$COMPOSE_FILE" .env.example scripts/ >>"$LOG_FILE" 2>&1 || true
    log "deployment files synced."
else
    log "WARN: git fetch timed out or failed (network unstable); continuing with current compose."
fi

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
