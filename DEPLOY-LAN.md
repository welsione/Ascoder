# 局域网拉取式部署（GitHub Actions + GHCR + cron）

适用于**服务器在内网、只能主动从 GitHub 拉取、GitHub 无法反向访问服务器**的场景。

## 架构

```
┌──────────────────────────────┐        push to master
│  github.com/welsione/Ascoder │ ─────────────────────────┐
└──────────────────────────────┘
        │                                   │
        │ ① GitHub Actions 构建 backend /   │
        │   frontend 镜像，push 到 GHCR     ▼
        │                      ┌─────────────────────────┐
        │                      │  ghcr.io               │
        │                      │  welsione/ascoder-     │
        │                      │   backend:latest       │
        │                      │  welsione/ascoder-     │
        │                      │   frontend:latest      │
        │                      └─────────────────────────┘
        │ ② 服务器每 5 分钟 cron：               ▲
        │    git fetch compose +                 │ docker pull
        │    docker compose pull ─────────────────┘
        ▼
┌──────────────────────────────┐
│  局域网服务器                 │
│  /opt/ascoder               │
│   ├─ docker-compose.prod.yml│  ← 用 image: 拉镜像
│   ├─ .env                    │  ← 生产配置（不入库）
│   ├─ data/                   │  ← 持久化（仓库 / 索引）
│   └─ scripts/server/         │
│       ├─ deploy.sh           │  ← cron 调用
│       └─ install.sh          │  ← 一次性安装
└──────────────────────────────┘
```

**关键点**：GitHub 完全不需要访问服务器。服务器主动拉取镜像与 compose 文件，重启容器。延迟取决于 cron 间隔（默认 5 分钟）。

## 前置条件（服务器）

| 依赖 | 说明 |
| --- | --- |
| Docker Engine + compose 插件 | v2 以上 |
| git | 浅克隆仓库用 |
| MySQL 8 | **跑在宿主机**上（容器通过 `host.docker.internal:3306` 连接） |
| 出网能力 | 能访问 `github.com` 与 `ghcr.io` |

> MySQL 也可以容器化，但当前 `docker-compose.prod.yml` 沿用 `host.docker.internal` 模式，与开发环境一致。若需容器化 MySQL，自行追加一个 mysql 服务即可。

## 一次性安装

在服务器上执行（建议 root 或有 docker 权限的用户）：

```bash
# 1. 拉取安装脚本（可只下载这一个文件）
mkdir -p /opt/ascoder
cd /opt/ascoder
curl -fsSL https://raw.githubusercontent.com/welsione/Ascoder/master/scripts/server/install.sh -o install.sh
bash install.sh
```

脚本会：

1. 检查 git / docker / docker compose；
2. 浅克隆仓库到 `/opt/ascoder`（仅用于同步 compose 与脚本，源码在镜像里）；
3. 从 `.env.example` 生成 `.env`；
4. 安装 cron，每 5 分钟跑 `scripts/server/deploy.sh`。

可覆盖默认值：

```bash
ASCODER_HOME=/srv/ascoder CRON_INTERVAL_MIN=2 bash install.sh
```

## 配置 .env

```bash
vi /opt/ascoder/.env
```

**必须配置**：

- `MYSQL_PASSWORD`：与宿主机 MySQL 中 `ascoder` 用户一致的密码
- `MYSQL_USER`：默认 `ascoder`，需在 MySQL 中先建好用户与库 `ascoder`
- 模型 Key：任选一种
  - `LLM_PROVIDER=database`（推荐，启动后在「设置 → 模型供应商」页面里配 Key / Base URL / 模型 ID）
  - 或直接设 `MINIMAX_API_KEY` / `ANTHROPIC_API_KEY` + `AGENT_MODEL_ID` + `AGENT_BASE_URL`

MySQL 初始化示例（在宿主机执行）：

```sql
CREATE DATABASE ascoder CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ascoder'@'%' IDENTIFIED BY 'your-strong-password';
GRANT ALL ON ascoder.* TO 'ascoder'@'%';
FLUSH PRIVILEGES;
```

## 验证

安装后首次手动触发一次拉取并启动：

```bash
bash /opt/ascoder/scripts/server/deploy.sh
```

查看容器状态：

```bash
cd /opt/ascoder
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

后端健康检查通过后，浏览器访问 `http://<服务器IP>:5173`。

## 自动更新机制

`install.sh` 安装的 cron 行（`crontab -l` 可见）：

```cron
*/5 * * * * /opt/ascoder/scripts/server/deploy.sh # ascoder-auto-deploy
```

`deploy.sh` 每次执行：

1. `git fetch` 最新 compose 与脚本（不触碰 `.env` / `data/`）；
2. `docker compose pull` 拉最新镜像；
3. `docker compose up -d`：**镜像未变则不重启容器**，避免无谓抖动；
4. 清理悬挂旧镜像。

日志写到 `/opt/ascoder/deploy.log`。

修改 cron 间隔：

```bash
# 改成每 2 分钟
( crontab -l | sed 's#\*/5#\*/2#' ) | crontab -
```

## 立即触发更新（不等 cron）

```bash
bash /opt/ascoder/scripts/server/deploy.sh
```

## 回滚

镜像每次构建会同时打两个 tag：`latest` 与 `master-<short-sha>`（GHCR Packages 页可见历史）。

```bash
cd /opt/ascoder
# 改 compose 里的 image tag 为某个历史 sha
sed -i 's#ascoder-backend:latest#ascoder-backend:master-abcd123#' docker-compose.prod.yml
sed -i 's#ascoder-frontend:latest#ascoder-frontend:master-abcd123#' docker-compose.prod.yml
docker compose -f docker-compose.prod.yml up -d
```

> 注意：下次 cron 会把 `docker-compose.prod.yml` 恢复成 `latest`。回滚期间临时停 cron 或注释掉镜像行：`crontab -e` 注释那一行。

## GHCR 私有仓库登录

仓库是 **public** 时，`docker pull` 无需认证。

仓库是 **private** 时，服务器需要先登录 GHCR（使用有 `read:packages` 权限的 Personal Access Token）：

```bash
echo "<your-PAT>" | docker login ghcr.io -u <github-username> --password-stdin
```

登录态保存在 `~/.docker/config.json`，cron 拉取会复用。镜像可见性在 GitHub → Packages → 对应包 → Package settings 管理。

## 故障排查

| 症状 | 原因 / 解决 |
| --- | --- |
| `deploy.sh` 报 image pull failed | 仓库 private 且未 `docker login ghcr.io`；或服务器无法访问 `ghcr.io`（检查出网 / 代理） |
| `deploy.sh` 日志出现 `git fetch timed out` | 服务器到 GitHub 网络不稳（老版 git / 慢网络常见）。deploy.sh 已容错：fetch 失败只跳过 compose 文件更新，仍会从 GHCR 拉镜像并重启--镜像更新不受影响。若 compose 结构有变更需手动同步：`cd /opt/ascoder && git fetch origin master && git checkout origin/master -- docker-compose.prod.yml scripts/`，或等下次 cron 自动重试 |
| backend 容器反复重启 | 多半是 MySQL 连不上：检查宿主机 MySQL 是否监听 `0.0.0.0:3306`、`MYSQL_PASSWORD` 是否正确、用户是否允许从 `host.docker.internal` 连接 |
| 前端能打开但接口 502 | backend 未通过 healthcheck；`docker compose logs backend` 看异常 |
| cron 没生效 | `crontab -l` 确认有 `ascoder-auto-deploy` 行；`systemctl status cron`（或 crond）确认服务在跑 |
| `git checkout` 覆盖了本地改动 | `deploy.sh` 只 checkout `docker-compose.prod.yml` / `.env.example` / `scripts/`，`.env` 与 `data/` 不会被覆盖。若需自定义 compose，用 override：`docker compose -f docker-compose.prod.yml -f docker-compose.override.yml up -d`，并在 `deploy.sh` 里追加 `-f` |
| 镜像没更新 | 确认 GitHub Actions 构建成功（仓库 → Actions 标签页）；GHCR 有缓存，首次构建较慢 |

## 与本地开发 compose 的区别

| | `docker-compose.yml` | `docker-compose.prod.yml` |
| --- | --- | --- |
| 启动方式 | `build:` 从源码构建 | `image:` 从 GHCR 拉取 |
| 用途 | 本地开发 | 服务器部署 |
| 其余配置 | —— | 完全一致（env / volume / healthcheck） |

本地开发仍用 `docker compose up -d --build`；服务器部署用 `docker compose -f docker-compose.prod.yml up -d`。
