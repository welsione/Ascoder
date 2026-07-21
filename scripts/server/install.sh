#!/usr/bin/env bash
# Ascoder 局域网部署一次性安装脚本。
#
# 在服务器上执行：bash scripts/server/install.sh
# 做四件事：
#   1. 检查依赖（git / docker / docker compose）
#   2. 浅克隆仓库到 ASCODER_HOME（默认 /opt/ascoder），仅用于同步 compose 与脚本
#   3. 从 .env.example 生成 .env（如不存在）
#   4. 安装 cron，定时调用 deploy.sh 拉取更新
#
# 可用环境变量覆盖默认值：
#   ASCODER_HOME=/opt/ascoder       部署目录
#   ASCODER_REPO=<repo url>         仓库地址
#   ASCODER_BRANCH=master           跟踪分支
#   CRON_INTERVAL_MIN=5             cron 拉取间隔（分钟）
set -euo pipefail

INSTALL_DIR="${ASCODER_HOME:-/opt/ascoder}"
REPO_URL="${ASCODER_REPO:-https://github.com/welsione/Ascoder.git}"
BRANCH="${ASCODER_BRANCH:-master}"
CRON_INTERVAL_MIN="${CRON_INTERVAL_MIN:-5}"

MARK="ascoder-auto-deploy"

echo "=== Ascoder LAN Deploy Installer ==="
echo "Install dir : $INSTALL_DIR"
echo "Repo        : $REPO_URL"
echo "Branch      : $BRANCH"
echo "Cron        : every ${CRON_INTERVAL_MIN} min"
echo ""

# 1. 依赖检查
command -v git >/dev/null || { echo "ERROR: git not found, install it first"; exit 1; }
command -v docker >/dev/null || { echo "ERROR: docker not found, install Docker Engine first"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "ERROR: docker compose plugin not found"; exit 1; }
command -v openssl >/dev/null || { echo "ERROR: openssl not found, install it first (used to generate ASCODER_ENCRYPTION_KEY)"; exit 1; }

# 2. 克隆或更新仓库（带超时与重试，应对到 GitHub 网络不稳定）
clone_or_sync() {
    if [ -d "$INSTALL_DIR/.git" ]; then
        echo "existing repo found at $INSTALL_DIR, syncing..."
        if ( cd "$INSTALL_DIR" && timeout 60 git fetch origin "$BRANCH" --depth=1 --quiet 2>/dev/null ); then
            ( cd "$INSTALL_DIR" && git checkout -B "$BRANCH" "origin/$BRANCH" --quiet )
            return 0
        fi
        echo "WARN: fetch failed (network unstable), keeping existing checkout."
        return 0
    fi
    mkdir -p "$(dirname "$INSTALL_DIR")"
    echo "cloning repo (shallow, with retries)..."
    for attempt in 1 2 3; do
        if timeout 180 git clone --depth=1 --branch "$BRANCH" "$REPO_URL" "$INSTALL_DIR" 2>/dev/null; then
            return 0
        fi
        echo "  clone attempt $attempt failed, retrying in 3s..."
        rm -rf "$INSTALL_DIR"
        sleep 3
    done
    return 1
}

clone_or_sync || { echo "ERROR: failed to clone/sync repo after retries (check network to github.com)"; exit 1; }

cd "$INSTALL_DIR"

# 3. 生成 .env（首次安装）
if [ ! -f .env ]; then
    cp .env.example .env
    echo "created .env from template."
else
    echo ".env already exists, keep it."
fi

# 3.5 自动生成 ASCODER_ENCRYPTION_KEY（未配置时）
# 非开发环境必须配置加密密钥（ApiKeyEncryptor 在非 dev profile 下不允许默认密钥）。
# 首次安装自动生成并持久化到 .env；如需自定义，在 .env 手动设置即可，脚本不会覆盖。
if ! grep -q "^ASCODER_ENCRYPTION_KEY=." .env 2>/dev/null; then
    KEY=$(openssl rand -base64 32)
    if grep -q "^ASCODER_ENCRYPTION_KEY=" .env 2>/dev/null; then
        awk -v k="$KEY" '/^ASCODER_ENCRYPTION_KEY=/{print "ASCODER_ENCRYPTION_KEY=" k; next} 1' .env > .env.tmp && mv .env.tmp .env
    else
        echo "ASCODER_ENCRYPTION_KEY=$KEY" >> .env
    fi
    echo "generated ASCODER_ENCRYPTION_KEY into .env (auto)"
fi

# 4. 安装 / 更新 cron
SCRIPT="$INSTALL_DIR/scripts/server/deploy.sh"
chmod +x "$SCRIPT"
CRON_LINE="*/${CRON_INTERVAL_MIN} * * * * ${SCRIPT} # ${MARK}"
( crontab -l 2>/dev/null | grep -v "$MARK"; echo "$CRON_LINE" ) | crontab -
echo "cron installed: $CRON_LINE"

# 5. 后续指引
cat <<EOF

=== Install complete ===

Next steps:
  1. Edit config:  vi $INSTALL_DIR/.env
       - set MYSQL_PASSWORD (must match your host MySQL user)
       - set LLM keys, or use the database provider in Settings UI
  2. Ensure MySQL 8 is running on the host at port 3306
       (backend connects via host.docker.internal:3306)
  3. (Only if repo is private) login to GHCR:
       echo "<PAT-with-read:packages>" | docker login ghcr.io -u <github-user> --password-stdin
  4. Start now:  $SCRIPT
       or wait for cron to fire within ${CRON_INTERVAL_MIN} min.

Logs: $INSTALL_DIR/deploy.log
Docs: $INSTALL_DIR/DEPLOY-LAN.md

EOF
