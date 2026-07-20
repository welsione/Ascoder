package cn.welsione.ascoder.config;

import cn.welsione.ascoder.agent.AgentProperties;
import cn.welsione.ascoder.codegraph.CodeGraphProperties;
import cn.welsione.ascoder.repository.git.GitProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启动装配：把 Ascoder 顶层 + 各子模块的 {@code @ConfigurationProperties} 全部注册到 Spring 上下文。
 *
 * <p>子模块的 Properties 类按"模块自管"原则分散到各自包内（agent / codegraph / git），
 * 在此处集中 import 与开启扫描，避免在每个使用点重复 @EnableConfigurationProperties。</p>
 */
@Configuration
@EnableConfigurationProperties({
        AscoderProperties.class,
        AgentProperties.class,
        CodeGraphProperties.class,
        GitProperties.class,
})
@Getter
@Setter
public class PropertiesConfiguration {
}