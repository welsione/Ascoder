package cn.welsione.ascoder.repository.git;

import cn.welsione.ascoder.common.SyncCommandRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Git 命令执行器，同步执行 Git 命令。
 *
 * <p>设置 {@code GIT_TERMINAL_PROMPT=0} 禁止 Git 在非交互环境中弹出凭据输入提示，
 * 避免进程因等待 stdin 而挂起。</p>
 *
 * <p>支持通过 {@code ascoder.git.http-proxy} 配置 HTTP 代理，
 * 让容器内 Git 通过代理访问 TLS 不兼容的远程服务器。</p>
 *
 * <p>通过 {@code GIT_CONFIG_COUNT} 注入 {@code safe.directory=*}，
 * 绕过 Git 2.35.2+ 的 dubious ownership 检查，避免 Docker 挂载卷属主不一致时拒绝操作。</p>
 *
 * <p>当 {@code ascoder.git.ssl-verify} 设为 {@code false} 时，额外注入 {@code http.sslVerify=false}，
 * 用于内网自签名证书或 GnuTLS 不兼容的 Git 服务器场景。</p>
 */
@Slf4j
@Component
public class GitCommandRunner extends SyncCommandRunner {

    @Value("${ascoder.git.http-proxy:}")
    private String httpProxy;

    @Value("${ascoder.git.ssl-verify:true}")
    private boolean sslVerify;

    @Override
    protected Map<String, String> getEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("GIT_TERMINAL_PROMPT", "0");
        // Docker 挂载卷的属主与容器用户不一致时，Git 2.35.2+ 会拒绝操作（dubious ownership）。
        // 通过环境变量注入 safe.directory=* 绕过检查，避免逐个仓库添加例外。
        env.put("GIT_CONFIG_COUNT", "1");
        env.put("GIT_CONFIG_KEY_0", "safe.directory");
        env.put("GIT_CONFIG_VALUE_0", "*");
        if (!sslVerify) {
            env.put("GIT_CONFIG_COUNT", "2");
            env.put("GIT_CONFIG_KEY_1", "http.sslVerify");
            env.put("GIT_CONFIG_VALUE_1", "false");
            log.info("Git SSL 证书验证已禁用（ascoder.git.ssl-verify=false）");
        }
        if (httpProxy != null && !httpProxy.isBlank()) {
            env.put("https_proxy", httpProxy);
            env.put("http_proxy", httpProxy);
            log.debug("Git 使用 HTTP 代理: {}", httpProxy);
        }
        return env;
    }
}
