package cn.welsione.ascoder.codegraph;

import cn.welsione.ascoder.codegraph.infrastructure.cli.CliCodeGraphClient;
import cn.welsione.ascoder.codegraph.infrastructure.cli.CodeGraphCommandRunner;
import cn.welsione.ascoder.codegraph.infrastructure.cli.IndexProgressTracker;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.runtime.application.RuntimeSettingsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * CodeGraph 子模块的 Spring Bean 注册。
 *
 * <p>ascoder-codegraph 是纯 Java 库（零 Spring 依赖），需要 Spring 管理的 Bean 在此集中注册。</p>
 *
 * <p>executable 走启动期注入（重启生效）；timeout / indexTimeout 走
 * {@link RuntimeSettingsService}（设置页可热改）。</p>
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
            RuntimeSettingsService runtimeSettings,
            CodeGraphCommandRunner codeGraphCommandRunner,
            IndexProgressTracker indexProgressTracker
    ) {
        // executable 启动期锁定；timeout / indexTimeout 每次调用前重读
        String executable = runtimeSettings.readString("codegraph.executable");
        return new CliCodeGraphClient(
                () -> new CodeGraphConfig(
                        executable,
                        Duration.ofSeconds(runtimeSettings.readLong("codegraph.timeout-seconds")),
                        Duration.ofSeconds(runtimeSettings.readLong("codegraph.index-timeout-seconds"))
                ),
                codeGraphCommandRunner,
                indexProgressTracker
        );
    }
}
