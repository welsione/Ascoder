# Ascoder Docker 部署

Ascoder 默认使用 Docker Compose 启动三个服务：

- `mysql`: MySQL 8.4，数据保存在 Docker volume
- `backend`: Spring Boot API，内置 git、Node.js 和 CodeGraph CLI
- `frontend`: Nginx 托管 Vue 静态资源，并反向代理 `/api` 到后端

默认只暴露一个入口端口：`APP_PORT`。浏览器访问 `http://localhost:5173` 即可使用，前端通过同源 `/api` 访问后端。

## 快速启动

```bash
cp .env.example .env
```

编辑 `.env`，至少填写一个可用的模型 API Key，例如：

```bash
MINIMAX_API_KEY=your_api_key
```

然后启动。

macOS / Linux：

```bash
./scripts/deploy.sh
```

Windows PowerShell：

```powershell
Copy-Item .env.example .env
notepad .env
powershell -ExecutionPolicy Bypass -File .\scripts\deploy.ps1
```

如果不使用脚本，也可以直接运行：

```bash
docker compose up -d --build
```

启动后访问：

```bash
http://localhost:5173
```

## 常用命令

```bash
docker compose ps
docker compose logs -f backend frontend
docker compose restart backend
docker compose down
```

停止并保留数据：

```bash
docker compose down
```

停止并删除数据库、仓库缓存和索引数据：

```bash
docker compose down -v
```

## 配置项

常用配置都在 `.env`：

- `APP_PORT`: Web 访问端口，默认 `5173`
- `MYSQL_PASSWORD`: 应用数据库密码
- `MYSQL_ROOT_PASSWORD`: MySQL root 密码
- `MINIMAX_API_KEY` / `ANTHROPIC_API_KEY` / `OPENAI_API_KEY`: 模型 API Key
- `AGENT_MODEL_ID`: 使用的模型
- `AGENT_BASE_URL`: 模型服务地址
- `JVM_OPTS`: 后端 JVM 参数

## 私有仓库访问

HTTPS Token：

```bash
GIT_USERNAME=git
GIT_TOKEN=your_token
```

SSH Key：

```bash
docker compose -f docker-compose.yml -f docker-compose.ssh.yml up -d --build
```

Windows 上使用 SSH Key 时，建议先确认密钥文件存在：

```powershell
Test-Path $env:USERPROFILE\.ssh\id_rsa
Test-Path $env:USERPROFILE\.ssh\known_hosts
```

如果你的密钥不是 `id_rsa`，请复制 `docker-compose.ssh.yml` 为 `docker-compose.override.yml`，把挂载路径改成自己的密钥文件，例如：

```yaml
services:
  backend:
    volumes:
      - ${USERPROFILE}/.ssh/id_ed25519:/home/ascoder/.ssh/id_rsa:ro
      - ${USERPROFILE}/.ssh/known_hosts:/home/ascoder/.ssh/known_hosts:ro
```

宿主机已有仓库目录：

```bash
HOST_REPO_PATH=/path/to/repos
docker compose -f docker-compose.yml -f docker-compose.host-repos.yml up -d --build
```

该模式会把后端的 `REPO_ROOT` 指向只读挂载目录 `/app/data/host-repos`，适合分析宿主机上已经存在的仓库。

Windows PowerShell 设置宿主机仓库目录示例：

```powershell
Add-Content .env "HOST_REPO_PATH=C:/Users/you/code"
docker compose -f docker-compose.yml -f docker-compose.host-repos.yml up -d --build
```

路径建议使用正斜杠，例如 `C:/Users/you/code`，避免 YAML 和 Compose 对反斜杠转义产生歧义。

## Windows 注意事项

- 使用 Docker Desktop，并确保启用 Linux containers。
- 项目建议放在 `C:\Users\<你的用户名>\...` 这类 Docker Desktop 默认可访问的位置。
- 第一次构建会下载 Maven、Node、Nginx、MySQL 和 CodeGraph 依赖，需要稳定网络。
- 如果 PowerShell 阻止脚本运行，可以使用：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\deploy.ps1
```

- 如果端口冲突，修改 `.env` 中的 `APP_PORT`，例如 `APP_PORT=8088`。

## Windows + WSL2 部署

Ascoder 的 Docker 容器在 Windows 上的最佳运行环境是 **WSL2 后端的 Docker Desktop**。

### 前置条件

1. Windows 10/11 Pro 或 Enterprise（家庭版可用 WSL2 但需手动启用 Hyper-V）
2. 安装 [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/)
3. 启用 WSL2 后端：Docker Desktop → Settings → General → **Use WSL2 based engine**（默认）
4. 安装 WSL2 发行版（推荐 Ubuntu）：`wsl --install -d Ubuntu`
5. 在 Docker Desktop → Settings → Resources → WSL Integration 中启用 Ubuntu

### 验证 WSL2 后端

```powershell
wsl -l -v
# 确认 DEFAULT VERSION = 2
docker info | Select-String "OSType"
# 应为 linux
```

### 路径语义

| 上下文 | `~` 解析为 | 建议路径前缀 |
| --- | --- | --- |
| PowerShell | `C:\Users\<you>` | `${HOME}\projects\ascoder` |
| WSL2 bash | `/home/<you>` | `~/projects/ascoder` |
| Docker 容器 | `/home/ascoder` | （容器内） |

**避免**：
- 把项目放在 `C:\Program Files\` 等系统目录（Docker Desktop 默认拒绝 bind mount）
- 把 `HOST_REPO_PATH` 指向网络驱动器（性能极差）
- 用反斜杠路径在 `.env`（`HOST_REPO_PATH=C:/Users/you/repos` 用正斜杠）

### 性能提示

- **推荐**：让 Ascoder 在容器内 `git clone`（走 named volume `repo_data`），避免 Windows bind mount 的 NTFS/9P 性能损耗。
- **不推荐**：把 Windows 目录作为 `HOST_REPO_PATH`（慢 3-10 倍）。
- **折中**：把仓库放在 WSL2 内部文件系统（`\\wsl$\Ubuntu\home\<you>\repos`）然后 bind mount。

### 已知陷阱

| 场景 | 症状 | 解决 |
| --- | --- | --- |
| 路径含空格（如 `C:\Users\John Doe\`） | `.env` 中路径被 compose 截断 | 始终用引号包路径；或把项目放在无空格路径 |
| PowerShell ExecutionPolicy | `install.ps1` 运行报"running scripts is disabled" | `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` 或 `powershell -ExecutionPolicy Bypass -File .\install.ps1` |
| 家庭版 Windows 10/11 | 没有 Hyper-V 默认无法跑 Docker Desktop | 安装 WSL2 内核更新后用 WSL2 后端；或升级到 Pro |
| 企业域账户 | `Set-ExecutionPolicy` 被 Group Policy 锁 | 只能用 `powershell -ExecutionPolicy Bypass -File` 单次覆盖 |
| 内网代理 | Docker Desktop 拉不到 Docker Hub 镜像 | Settings → Resources → Proxies 配置 HTTP/HTTPS 代理；或预 `docker save` 离线 tarball |
| 旧版 install.sh 注册过 launchd / systemd | 升级后 `docker compose up` 报端口占用 | `launchctl unload` / `systemctl stop ascoder` + 删除 plist/unit |

### 升级指引

如果你之前使用旧版 `install.sh` + `java -jar` 部署方式，升级到 Docker Compose 模式需要：

1. **停止旧服务**：
   - macOS: `launchctl unload ~/Library/LaunchAgents/com.ascoder.service.plist && rm ~/Library/LaunchAgents/com.ascoder.service.plist`
   - Linux: `sudo systemctl stop ascoder && sudo systemctl disable ascoder && sudo rm /etc/systemd/system/ascoder.service`
2. **迁移数据**（如需保留）：导出 MySQL 数据 → 导入到 Docker volume 的 MySQL
3. **重新安装**：`./installer/install.sh`（新版本会走 Docker Compose）

## Git Extra Hosts

如果你的私有 Git 托管在自建 GitLab / Bitbucket / Gitea 等非公网 host，可以通过 `GIT_EXTRA_HOSTS` 让容器内的 git 凭证覆盖这些 host：

```bash
GIT_EXTRA_HOSTS=git.example.com,gitlab.internal.corp
```

**注意**：通配符（如 `*.example.com`）不被支持——git credential store 不匹配 globs。如需通配符支持，请等待后续版本（按项目隔离的凭证管理）。

## 健康检查

后端健康检查：

```bash
docker compose exec backend curl -fsS http://127.0.0.1:18080/api/health
```

前端健康检查：

```bash
curl -fsS http://localhost:5173/
```
