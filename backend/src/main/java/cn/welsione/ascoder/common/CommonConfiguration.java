package cn.welsione.ascoder.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ascoder-common 子模块的 Spring Bean 注册。
 *
 * <p>子模块是纯 Java 库（零 Spring 依赖），需要 Spring 管理的 Bean 在此集中注册。</p>
 */
@Configuration
public class CommonConfiguration {

    @Bean
    public SafeCommandRunner safeCommandRunner() {
        return new SafeCommandRunner();
    }

    @Bean
    public FilePathSanitizer filePathSanitizer() {
        return new FilePathSanitizer();
    }
}
