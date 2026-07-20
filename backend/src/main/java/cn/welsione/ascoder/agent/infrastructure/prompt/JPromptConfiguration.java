package cn.welsione.ascoder.agent.infrastructure.prompt;

import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.jprompt.loader.ClasspathTemplateLoader;
import cn.welsione.jprompt.loader.CompositeTemplateLoader;
import cn.welsione.jprompt.loader.TemplateLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 根据环境配置初始化 JPrompt 加载器。
 *
 * <p>读取 {@code ascoder.prompt.source} 配置属性决定加载优先级：
 * <ul>
 *   <li>{@code file}（默认，开发环境）：classpath 文件优先</li>
 *   <li>{@code db}（生产环境）：数据库优先</li>
 * </ul>
 * 构造 {@link CompositeTemplateLoader} 并注入 {@link PromptManager}。</p>
 */
@Slf4j
@Configuration
public class JPromptConfiguration {

    @Bean
    public CompositeTemplateLoader compositeTemplateLoader() {
        TemplateLoader classpathLoader = new ClasspathTemplateLoader();
        // fallback 为空加载器，数据库兜底由 PromptManager 内部处理
        TemplateLoader noopFallback = path -> { throw new cn.welsione.jprompt.TemplateException("No fallback loader configured for: " + path); };
        return new CompositeTemplateLoader(classpathLoader, noopFallback);
    }

    @Bean
    public PromptManager promptManager(AgentConfigService agentConfigService,
                                       CompositeTemplateLoader compositeLoader,
                                       @Value("${ascoder.prompt.source:file}") String source) {
        boolean dbFirst = "db".equalsIgnoreCase(source);
        log.info("初始化 PromptManager，ascoder.prompt.source={}，dbFirst={}", source, dbFirst);
        return new PromptManager(agentConfigService, compositeLoader, dbFirst);
    }
}
