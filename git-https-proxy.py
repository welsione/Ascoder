#!/usr/bin/env python3
"""简单的 HTTPS CONNECT 代理，用于让 Docker 容器内的 Git 通过宿主机访问 TLS 不兼容的服务器。

用法：
  python3 git-https-proxy.py [port]

然后在 docker-compose.yml 中设置：
  GIT_HTTP_PROXY: http://host.docker.internal:<port>
"""
import socket
import threading
import sys
import select

DEFAULT_PORT = 8443

class ConnectProxy:
    def __init__(self, port):
        self.port = port

    def start(self):
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(('0.0.0.0', self.port))
        server.listen(50)
        print(f"HTTPS CONNECT 代理已启动，端口 {self.port}")
        while True:
            client, addr = server.accept()
            threading.Thread(target=self.handle, args=(client,), daemon=True).start()

    def handle(self, client):
        try:
            request = b''
            while b'\r\n\r\n' not in request:
                chunk = client.recv(4096)
                if not chunk:
                    client.close()
                    return
                request += chunk

            lines = request.decode('utf-8', errors='replace').split('\r\n')
            method, target, _ = lines[0].split(' ', 2)

            if method == 'CONNECT':
                host, port = target.split(':')
                port = int(port)
                remote = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                remote.settimeout(10)
                remote.connect((host, port))
                client.sendall(b'HTTP/1.1 200 Connection Established\r\n\r\n')
                self.relay(client, remote)
            else:
                # 非 CONNECT 请求，直接转发
                url_parts = target.split('://', 1)[1] if '://' in target else target
                host_port = url_parts.split('/')[0]
                if ':' in host_port:
                    host, port = host_port.split(':')
                    port = int(port)
                else:
                    host = host_port
                    port = 443 if method == 'HTTPS' else 80

                remote = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                remote.settimeout(10)
                remote.connect((host, port))
                remote.sendall(request)
                self.relay(client, remote)
        except Exception as e:
            pass
        finally:
            try:
                client.close()
            except:
                pass

    def relay(self, client, remote):
        """双向转发数据。"""
        client.setblocking(False)
        remote.setblocking(False)
        timeout = 60
        while True:
            readable, _, _ = select.select([client, remote], [], [], timeout)
            if not readable:
                break
            for sock in readable:
                try:
                    data = sock.recv(65536)
                    if not data:
                        return
                    if sock is client:
                        remote.sendall(data)
                    else:
                        client.sendall(data)
                except:
                    return

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    ConnectProxy(port).start()
