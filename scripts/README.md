# scripts/

仓库根目录的运维与开发辅助脚本。所有脚本设计为在仓库根目录运行。

## 部署与验证

### `verify-docker.sh`

Docker Compose 一键验证脚本。检查镜像构建、服务启动、数据库迁移、
前端可访问性等是否正常，作为发布前的 smoke test。

```bash
bash scripts/verify-docker.sh
```

### `server/` - 局域网部署脚本

一次性安装 + 定时自动更新，适用于服务器在局域网、只能主动从 GitHub 拉取的场景。
详见 [DEPLOY-LAN.md](../DEPLOY-LAN.md)。

| 脚本 | 平台 | 用途 |
| --- | --- | --- |
| `server/install.sh` | Linux / macOS | 检查依赖、克隆仓库、生成 .env、安装 cron |
| `server/deploy.sh` | Linux / macOS | cron 定时调用：拉取镜像 + 重启容器 |
| `server/install.ps1` | Windows | 检查依赖、克隆仓库、生成 .env、注册计划任务 |
| `server/deploy.ps1` | Windows | 计划任务调用：拉取镜像 + 重启容器 |

```bash
# Linux / macOS
bash scripts/server/install.sh

# Windows PowerShell
powershell -ExecutionPolicy Bypass -File scripts/server/install.ps1
```

### `dev.sh` - 测试环境管理

本地开发一键脚本，管理 dev-local / dev-docker 两种模式的容器启停。

```bash
bash scripts/dev.sh dev-local up    # 启动测试数据库
bash scripts/dev.sh dev-local down  # 停止
bash scripts/dev.sh dev-docker up   # 全容器开发
```

详见 [DEVELOPMENT.md](../DEVELOPMENT.md)。

## Git 代理（解决容器内 GnuTLS 不兼容）

Docker 容器内的 Git 与部分 TLS 服务器握手失败时使用下面的代理。
代理在 **宿主机** 启动，容器内的 Git 通过 `host.docker.internal` 访问。

### `git-https-proxy.py`

HTTPS CONNECT 代理。容器内的 Git 把 `GIT_HTTP_PROXY` 指向它，
由宿主机的 socket 层代为建立 HTTPS 连接，绕过容器内的 TLS 问题。

```bash
python3 scripts/git-https-proxy.py [port]   # 默认 8443
# docker-compose.yml: GIT_HTTP_PROXY: http://host.docker.internal:<port>
```

### `git-tls-proxy.py`

TLS 终结代理。容器内的 Git 使用 `http://` 访问本代理，
代理再用 `https://` 转发到目标服务器，TLS 由宿主机的 Python（OpenSSL）处理。
比 `git-https-proxy.py` 适用面更广——支持任意 HTTP 请求方法、请求头、请求体。

```bash
python3 scripts/git-tls-proxy.py [port]      # 默认 8443
# 容器内 Git 配置：
# git config --system url."http://host.docker.internal:<port>/https://".insteadOf "https://"
```

## 何时用哪个

| 场景 | 推荐脚本 |
|---|---|
| `git clone/fetch` 报 GnuTLS / TLS handshake 失败 | 先试 `git-tls-proxy.py`，不行再换 `git-https-proxy.py` |
| HTTP 客户端（含 Java 代码）调用 `https://` 资源 | `git-tls-proxy.py` |

两个代理互斥使用一个即可，不要同时启动占用同一端口。
