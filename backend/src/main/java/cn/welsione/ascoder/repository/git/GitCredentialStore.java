package cn.welsione.ascoder.repository.git;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理 ~/.git-credentials 文件，提供仓库级凭据的读写操作。
 *
 * <p>与 Docker 入口脚本写入的全局凭据共存：本类仅追加/更新特定 host 的凭据条目，
 * 不删除全局凭据（github.com / gitlab.com / gitee.com 等）。</p>
 *
 * <p>当配置了 TLS 终结代理（{@code ascoder.git.tls-proxy}）时，
 * 会额外为代理 URL 写入一份凭据，使 Git 在通过代理访问时能自动认证。</p>
 */
@Slf4j
@Component
public class GitCredentialStore {

    private static final String DEFAULT_CREDENTIALS_PATH =
            System.getProperty("user.home") + "/.git-credentials";

    @Value("${ascoder.git.tls-proxy:}")
    private String tlsProxy;

    /**
     * 返回凭据文件路径，子类可覆盖以支持测试。
     */
    protected String getCredentialsPath() {
        return DEFAULT_CREDENTIALS_PATH;
    }

    /**
     * 将仓库级凭据写入 ~/.git-credentials，若该 host 已有条目则更新。
     *
     * <p>同时为 TLS 终结代理 URL 写入一份凭据（如果配置了代理），
     * 因为 Git 的 insteadOf 会将 HTTPS URL 重写为代理的 HTTP URL，
     * credential helper 会匹配重写后的 URL。</p>
     *
     * @param remoteUrl 远程仓库 URL（用于提取 host）
     * @param username  认证用户名
     * @param password  认证密码
     */
    public void upsert(String remoteUrl, String username, String password) {
        String host = extractHost(remoteUrl);
        if (host == null) {
            log.warn("无法从 remoteUrl 提取 host，跳过凭据写入：{}", remoteUrl);
            return;
        }

        // 写入 HTTPS 凭据（标准格式）
        writeEntry("https://", host, username, password);

        // 如果配置了 TLS 终结代理，额外写入代理 URL 的凭据
        if (tlsProxy != null && !tlsProxy.isBlank()) {
            String proxyHost = extractHostAndPort(tlsProxy);
            if (proxyHost != null) {
                writeEntry("http://", proxyHost, username, password);
                log.info("同时写入 TLS 代理凭据 host={}", proxyHost);
            }
        }
    }

    /**
     * 删除指定 host 的凭据条目。
     *
     * @param remoteUrl 远程仓库 URL（用于提取 host）
     */
    public void remove(String remoteUrl) {
        String host = extractHost(remoteUrl);
        if (host == null) {
            return;
        }

        Path path = Path.of(getCredentialsPath());
        if (!Files.exists(path)) {
            return;
        }

        try {
            String hostSuffix = "@" + host;
            List<String> remaining = Files.readAllLines(path).stream()
                    .filter(line -> !line.endsWith(hostSuffix))
                    .collect(Collectors.toList());

            Files.writeString(path, String.join("\n", remaining) + "\n",
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("删除凭据文件中 host={} 的条目", host);
        } catch (IOException ex) {
            log.error("更新凭据文件失败：{}", ex.getMessage(), ex);
        }
    }

    /**
     * 检查指定 host 是否已有凭据条目。
     */
    public boolean hasCredential(String remoteUrl) {
        Set<String> hosts = configuredHosts();
        String host = extractHost(remoteUrl);
        return host != null && hosts.contains(host);
    }

    /**
     * 获取当前凭据文件中已配置的所有 host。
     */
    public Set<String> configuredHosts() {
        Path path = Path.of(getCredentialsPath());
        if (!Files.exists(path)) {
            return Set.of();
        }

        try {
            return Files.readAllLines(path).stream()
                    .filter(line -> (line.startsWith("https://") || line.startsWith("http://")) && line.contains("@"))
                    .map(line -> {
                        int at = line.lastIndexOf('@');
                        return at >= 0 ? line.substring(at + 1) : null;
                    })
                    .filter(h -> h != null && !h.isBlank())
                    .collect(Collectors.toSet());
        } catch (IOException ex) {
            log.error("读取凭据文件失败：{}", ex.getMessage(), ex);
            return Set.of();
        }
    }

    /**
     * 写入一条凭据条目，若该 host 已有同 scheme 的条目则更新。
     */
    private void writeEntry(String scheme, String host, String username, String password) {
        Path path = Path.of(getCredentialsPath());
        String newLine = scheme + username + ":" + password + "@" + host;

        try {
            if (!Files.exists(path)) {
                Files.writeString(path, newLine + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                log.info("创建凭据文件并写入 host={}", host);
                return;
            }

            List<String> lines = Files.readAllLines(path);
            String hostSuffix = "@" + host;

            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith(scheme) && line.endsWith(hostSuffix)) {
                    lines.set(i, newLine);
                    replaced = true;
                    break;
                }
            }

            if (!replaced) {
                lines.add(newLine);
            }

            Files.writeString(path, String.join("\n", lines) + "\n",
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("更新凭据文件 scheme={} host={}，操作={}", scheme, host, replaced ? "替换" : "追加");
        } catch (IOException ex) {
            log.error("写入凭据文件失败：{}", ex.getMessage(), ex);
        }
    }

    /**
     * 从 URL 中提取 host[:port] 部分，使用字符串解析避免 URI.create() 的 percent-encoding。
     */
    private String extractHost(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }
        String url = remoteUrl.trim();
        if (url.startsWith("https://")) {
            url = url.substring(8);
        } else if (url.startsWith("http://")) {
            url = url.substring(7);
        } else if (url.startsWith("ssh://")) {
            url = url.substring(6);
        } else {
            return null;
        }
        int slash = url.indexOf('/');
        if (slash >= 0) {
            url = url.substring(0, slash);
        }
        int at = url.indexOf('@');
        if (at >= 0) {
            url = url.substring(at + 1);
        }
        return url.isEmpty() ? null : url;
    }

    /**
     * 从代理 URL 中提取 host:port 部分，使用字符串解析避免 URI.create() 的 percent-encoding。
     */
    private String extractHostAndPort(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return null;
        }
        String url = proxyUrl.trim();
        if (url.startsWith("https://")) {
            url = url.substring(8);
        } else if (url.startsWith("http://")) {
            url = url.substring(7);
        } else {
            return null;
        }
        int slash = url.indexOf('/');
        if (slash >= 0) {
            url = url.substring(0, slash);
        }
        return url.isEmpty() ? null : url;
    }
}
