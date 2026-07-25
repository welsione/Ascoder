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
│       ├─ deploy.sh           │  ← cron 调用（Linux）
│       ├─ deploy.ps1          │  ← 计划任务调用（Windows）
│       ├─ install.sh          │  ← 一次性安装（Linux）
│       └─ install.ps1         │  ← 一次性安装（Windows）
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

## Windows 服务器部署

Windows 上使用 PowerShell 脚本（`install.ps1` / `deploy.ps1`）替代 bash 版本，用**计划任务**替代 cron。架构与 Linux 版完全一致：GitHub Actions 推镜像到 GHCR，服务器定时拉取镜像与 compose 文件后重启容器。

### 前置条件（Windows）

| 依赖 | 说明 |
| --- | --- |
| Docker Desktop for Windows | 启用 WSL2 后端（默认）；确保 C 盘已共享给 Docker（Settings -> Resources -> File sharing） |
| Git for Windows | 提供 `git` 与可选的 `openssl` |
| MySQL 8 | 跑在宿主机上，监听 `0.0.0.0:3306`（容器通过 `host.docker.internal:3306` 连接） |
| PowerShell 5.1+ | Windows 10/11 自带；执行策略需允许本地脚本 |
| 出网能力 | 能访问 `github.com` 与 `ghcr.io` |

> **路径要求**：部署目录不要含空格或中文（如 `C:\ascoder`），避免 `.env` 路径解析与 Docker bind mount 异常。`.env` 中的路径一律用正斜杠（`C:/Users/you/repos`）。

### 一次性安装

以管理员身份打开 PowerShell：

```powershell
# 下载安装脚本（或直接 clone 仓库后执行）
mkdir C:\ascoder -Force
cd C:\ascoder
Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/welsione/Ascoder/master/scripts/server/install.ps1' -OutFile install.ps1

# 执行安装（默认部署到 C:\ascoder，每 5 分钟拉取一次）
powershell -ExecutionPolicy Bypass -File install.ps1
```

自定义部署目录与间隔：

```powershell
$env:ASCODER_HOME = 'D:\ascoder'
$env:CRON_INTERVAL_MIN = '2'
powershell -ExecutionPolicy Bypass -File install.ps1
```

脚本会：

1. 检查 `git` / `docker` / `docker compose`；
2. 浅克隆仓库到 `C:\ascoder`（仅用于同步 compose 与脚本，源码在镜像里）；
3. 从 `.env.example` 生成 `.env`，自动生成 `ASCODER_ENCRYPTION_KEY`（用 openssl 或 .NET 兜底）；
4. 注册 Windows 计划任务 `AscoderAutoDeploy`，每 5 分钟跑 `scripts\server\deploy.ps1`。

### 配置 .env

```powershell
notepad C:\ascoder\.env
```

与 Linux 版配置项完全一致：`MYSQL_PASSWORD`、`MYSQL_USER`、模型 Key 等。

### 计划任务管理

```powershell
# 查看任务状态
Get-ScheduledTask -TaskName AscoderAutoDeploy

# 立即触发一次（不等间隔）
Start-ScheduledTask -TaskName AscoderAutoDeploy

# 停止自动更新（如需锁定版本）
Disable-ScheduledTask -TaskName AscoderAutoDeploy

# 恢复自动更新
Enable-ScheduledTask -TaskName AscoderAutoDeploy

# 查看最近运行结果
Get-ScheduledTaskInfo -TaskName AscoderAutoDeploy

# 删除任务
Unregister-ScheduledTask -TaskName AscoderAutoDeploy -Confirm:$false
```

> 计划任务以当前登录用户身份运行（`LogonType Interactive`），需要用户保持登录。如需无人值守，可改用 SYSTEM 账户注册，但 Docker Desktop 需配置为 Windows 服务模式。

### 立即触发更新（不等计划任务）

```powershell
powershell -ExecutionPolicy Bypass -File C:\ascoder\scripts\server\deploy.ps1
```

日志写入 `C:\ascoder\deploy.log`。

### Windows 特有注意事项

| 场景 | 症状 / 解决 |
| --- | --- |
| PowerShell 执行策略阻止脚本 | `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`；或单次绕过：`powershell -ExecutionPolicy Bypass -File ...` |
| 企业域账户锁死执行策略 | 只能用 `-ExecutionPolicy Bypass` 单次覆盖 |
| 计划任务未触发 | 确认用户已登录（Interactive 模式）；或改用 `schtasks /query /tn AscoderAutoDeploy` 排查 |
| `host.docker.internal` 连不上 MySQL | Docker Desktop 默认支持；若被防火墙拦截，检查 Windows Defender 防火墙是否放行 3306 端口 |
| bind mount 权限不足 | Docker Desktop -> Settings -> Resources -> File sharing 确认 C 盘已共享 |
| 路径含空格导致 `.env` 截断 | 始终把项目放在无空格路径（如 `C:\ascoder`） |
| 符号链接创建失败 | 项目空间 worktree 链接在 Windows 上用 junction（`mklink /J`），无需管理员权限；如失败检查目录是否被占用 |

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
| Windows 计划任务没生效 | `Get-ScheduledTask -TaskName AscoderAutoDeploy` 确认状态为 Ready；`Get-ScheduledTaskInfo` 看上次运行结果；确认注册时的用户已登录（Interactive 模式） |
| Windows `deploy.ps1` 报执行策略错误 | 用 `powershell -ExecutionPolicy Bypass -File deploy.ps1` 运行；或 `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` |
| `git checkout` 覆盖了本地改动 | `deploy.sh` 只 checkout `docker-compose.prod.yml` / `.env.example` / `scripts/`，`.env` 与 `data/` 不会被覆盖。若需自定义 compose，用 override：`docker compose -f docker-compose.prod.yml -f docker-compose.override.yml up -d`，并在 `deploy.sh` 里追加 `-f` |
| 镜像没更新 | 确认 GitHub Actions 构建成功（仓库 → Actions 标签页）；GHCR 有缓存，首次构建较慢 |

## 与本地开发 compose 的区别

| | `docker-compose.yml` | `docker-compose.prod.yml` |
| --- | --- | --- |
| 启动方式 | `build:` 从源码构建 | `image:` 从 GHCR 拉取 |
| 用途 | 本地开发 | 服务器部署 |
| 其余配置 | —— | 完全一致（env / volume / healthcheck） |

本地开发仍用 `docker compose up -d --build`；服务器部署用 `docker compose -f docker-compose.prod.yml up -d`。
