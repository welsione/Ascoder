# scripts/

仓库根目录的运维与开发辅助脚本。所有脚本设计为在仓库根目录运行。

## 部署与验证

### `verify-docker.sh`

Docker Compose 一键验证脚本。检查镜像构建、服务启动、数据库迁移、
前端可访问性等是否正常，作为发布前的 smoke test。

```bash
bash scripts/verify-docker.sh
```

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
