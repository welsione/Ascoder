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
 */
@Slf4j
@Component
public class GitCommandRunner extends SyncCommandRunner {

    @Value("${ascoder.git.http-proxy:}")
    private String httpProxy;

    @Override
    protected Map<String, String> getEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("GIT_TERMINAL_PROMPT", "0");
        if (httpProxy != null && !httpProxy.isBlank()) {
            env.put("https_proxy", httpProxy);
            env.put("http_proxy", httpProxy);
            log.debug("Git 使用 HTTP 代理: {}", httpProxy);
        }
        return env;
    }
}
