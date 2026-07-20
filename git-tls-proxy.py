#!/usr/bin/env python3
"""TLS 终结代理：为 Docker 容器内的 Git 解决 GnuTLS 不兼容问题。

代理在宿主机上监听，接受 HTTP 请求，以 HTTPS 转发到目标服务器。
容器内的 Git 使用 http:// 代替 https:// 访问此代理。

用法：
  1. 启动代理：python3 git-tls-proxy.py 8443
  2. 在容器内设置 Git insteadOf：
     git config --system url."http://host.docker.internal:8443/https://".insteadOf "https://"
  3. 或在 Java 代码中通过 GIT_CONFIG_COUNT 等环境变量设置

这样容器内的 Git 发送 HTTP 请求到代理，代理以 HTTPS 转发到目标服务器，
TLS 由宿主机的 Python（OpenSSL）处理，绕过容器内的 GnuTLS 问题。
"""
import http.server
import ssl
import urllib.request
import urllib.error
import sys
import threading
import os

DEFAULT_PORT = 8443


class TLSProxyHandler(http.server.BaseHTTPRequestHandler):
    """HTTP 代理处理器，将请求以 HTTPS 转发到目标服务器。"""

    def do_GET(self):
        self._proxy_request()

    def do_POST(self):
        self._proxy_request()

    def do_HEAD(self):
        self._proxy_request()

    def do_PUT(self):
        self._proxy_request()

    def do_DELETE(self):
        self._proxy_request()

    def do_OPTIONS(self):
        self._proxy_request()

    def _proxy_request(self):
        # URL 格式: http://host:port/https://target.host/path
        target_url = self.path

        # 去掉前导 /
        if target_url.startswith("/https://") or target_url.startswith("/http://"):
            target_url = target_url[1:]
        else:
            self.send_error(400, f"Invalid URL format: {self.path}")
            return

        try:
            # 读取请求体
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length) if content_length > 0 else None

            # 构建转发请求
            headers = {}
            for key, value in self.headers.items():
                # 跳过 hop-by-hop 头
                if key.lower() in ("host", "connection", "proxy-authorization",
                                   "transfer-encoding", "content-length"):
                    continue
                headers[key] = value

            req = urllib.request.Request(target_url, data=body, headers=headers, method=self.command)

            # 发送 HTTPS 请求
            ctx = ssl.create_default_context()
            with urllib.request.urlopen(req, context=ctx, timeout=60) as resp:
                # 返回响应
                self.send_response(resp.status)
                for key, value in resp.getheaders():
                    if key.lower() in ("transfer-encoding", "connection"):
                        continue
                    self.send_header(key, value)
                self.end_headers()

                # 流式传输响应体
                while True:
                    chunk = resp.read(65536)
                    if not chunk:
                        break
                    self.wfile.write(chunk)

        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            for key, value in e.headers.items():
                if key.lower() in ("transfer-encoding", "connection"):
                    continue
                self.send_header(key, value)
            self.end_headers()
            if e.body:
                self.wfile.write(e.body)
        except Exception as e:
            self.send_error(502, f"Proxy error: {str(e)}")

    def log_message(self, format, *args):
        """简化日志输出。"""
        print(f"[{self.command}] {args[0]}")

    def version_string(self):
        return "Ascoder-TLS-Proxy/1.0"


class ThreadedHTTPServer(http.server.ThreadingHTTPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    server = ThreadedHTTPServer(('0.0.0.0', port), TLSProxyHandler)
    print(f"Ascoder TLS 终结代理已启动，端口 {port}")
    print(f"使用方式：在容器中设置 git config url.\"http://host.docker.internal:{port}/https://\".insteadOf \"https://\"")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n代理已停止")
        server.shutdown()


if __name__ == '__main__':
    main()
