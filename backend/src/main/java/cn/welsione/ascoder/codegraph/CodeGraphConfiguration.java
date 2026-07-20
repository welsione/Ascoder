package cn.welsione.ascoder.codegraph;

import cn.welsione.ascoder.codegraph.infrastructure.cli.CliCodeGraphClient;
import cn.welsione.ascoder.codegraph.infrastructure.cli.CodeGraphCommandRunner;
import cn.welsione.ascoder.codegraph.infrastructure.cli.IndexProgressTracker;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * CodeGraph 子模块的 Spring Bean 注册。
 *
 * <p>ascoder-codegraph 是纯 Java 库（零 Spring 依赖），需要 Spring 管理的 Bean 在此集中注册。</p>
 */
@Configuration
public class CodeGraphConfiguration {

    @Bean
    public CodeGraphCommandRunner codeGraphCommandRunner() {
        return new CodeGraphCommandRunner(Map.of(
                "LANG", "en_US.UTF-8",
                "LC_ALL", "en_US.UTF-8",
                "LC_CTYPE", "en_US.UTF-8",
                "PYTHONIOENCODING", "UTF-8"
        ));
    }

    @Bean
    public IndexProgressTracker indexProgressTracker() {
        return new IndexProgressTracker();
    }

    @Bean
    public CodeGraphClient codeGraphClient(
            @Value("${ascoder.codegraph.executable:codegraph}") String executable,
            @Value("${ascoder.codegraph.timeout-seconds:300}") long timeoutSeconds,
            @Value("${ascoder.codegraph.index-timeout-seconds:3600}") long indexTimeoutSeconds,
            CodeGraphCommandRunner codeGraphCommandRunner,
            IndexProgressTracker indexProgressTracker
    ) {
        CodeGraphConfig config = new CodeGraphConfig(
                executable,
                Duration.ofSeconds(timeoutSeconds),
                Duration.ofSeconds(indexTimeoutSeconds)
        );
        return new CliCodeGraphClient(config, codeGraphCommandRunner, indexProgressTracker);
    }
}
