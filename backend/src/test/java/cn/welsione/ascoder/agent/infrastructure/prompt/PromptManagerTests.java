package cn.welsione.ascoder.agent.infrastructure.prompt;

import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.jprompt.TemplateException;
import cn.welsione.jprompt.loader.ClasspathTemplateLoader;
import cn.welsione.jprompt.loader.CompositeTemplateLoader;
import cn.welsione.jprompt.loader.TemplateLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PromptManager 单元测试，覆盖环境感知双源加载的核心场景。
 */
class PromptManagerTests {

    private AgentConfigService agentConfigService;
    private CompositeTemplateLoader loader;

    @BeforeEach
    void setUp() {
        agentConfigService = mock(AgentConfigService.class);
        TemplateLoader noopFallback = path -> { throw new TemplateException("No fallback: " + path); };
        loader = new CompositeTemplateLoader(new ClasspathTemplateLoader(), noopFallback);
    }

    // --- getSystemPrompt ---

    @Test
    void getSystemPrompt_fileMode_returnsClasspathContent() {
        PromptManager manager = new PromptManager(agentConfigService, loader, false);
        String result = manager.getSystemPrompt("self-learning-insight");
        assertThat(result).containsIgnoringCase("Self Learning");
    }

    @Test
    void getSystemPrompt_fileMode_classpathNotExists_fallsBackToDb() {
        AgentConfig config = mock(AgentConfig.class);
        when(config.getSystemPrompt()).thenReturn("DB system prompt for unknown-agent");
        when(agentConfigService.getByAgentId("unknown-agent")).thenReturn(Optional.of(config));

        PromptManager manager = new PromptManager(agentConfigService, loader, false);
        String result = manager.getSystemPrompt("unknown-agent");
        assertThat(result).isEqualTo("DB system prompt for unknown-agent");
    }

    @Test
    void getSystemPrompt_dbMode_returnsDbContent() {
        AgentConfig config = mock(AgentConfig.class);
        when(config.getSystemPrompt()).thenReturn("DB system prompt for insight");
        when(agentConfigService.getByAgentId("self-learning-insight")).thenReturn(Optional.of(config));

        PromptManager manager = new PromptManager(agentConfigService, loader, true);
        String result = manager.getSystemPrompt("self-learning-insight");
        assertThat(result).isEqualTo("DB system prompt for insight");
    }

    @Test
    void getSystemPrompt_dbMode_dbEmpty_fallsBackToClasspath() {
        AgentConfig config = mock(AgentConfig.class);
        when(config.getSystemPrompt()).thenReturn("");
        when(agentConfigService.getByAgentId("self-learning-insight")).thenReturn(Optional.of(config));

        PromptManager manager = new PromptManager(agentConfigService, loader, true);
        String result = manager.getSystemPrompt("self-learning-insight");
        assertThat(result).containsIgnoringCase("Self Learning");
    }

    @Test
    void getSystemPrompt_bothEmpty_returnsNull() {
        when(agentConfigService.getByAgentId("unknown-agent")).thenReturn(Optional.empty());

        PromptManager manager = new PromptManager(agentConfigService, loader, true);
        String result = manager.getSystemPrompt("unknown-agent");
        assertThat(result).isNull();
    }

    // --- renderFramework ---

    @Test
    void renderFramework_alwaysFromClasspath() {
        PromptManager manager = new PromptManager(agentConfigService, loader, true);
        String result = manager.getFramework("prompts/agentscope-tooling-warnings.md");
        assertThat(result).isNotBlank();
    }

    // --- getFramework ---

    @Test
    void getFramework_alwaysFromClasspath() {
        PromptManager manager = new PromptManager(agentConfigService, loader, true);
        String result = manager.getFramework("prompts/agentscope-tooling-warnings.md");
        assertThat(result).isNotBlank();
    }

    // --- getAnswerStyle ---

    @Test
    void getAnswerStyle_alwaysFromClasspath() {
        PromptManager manager = new PromptManager(agentConfigService, loader, true);
        String result = manager.getAnswerStyle("developer");
        assertThat(result).isNotBlank();
    }

    // --- renderTaskTemplate ---

    @Test
    void renderTaskTemplate_dbMode_usesDbTemplate() {
        AgentConfig config = mock(AgentConfig.class);
        when(config.getTaskTemplate()).thenReturn("项目空间: {{projectSpaceName}}，问题: {{question}}");
        when(agentConfigService.getByAgentId("self-learning-insight")).thenReturn(Optional.of(config));

        PromptManager manager = new PromptManager(agentConfigService, loader, true);
        TaskPromptContext data = TaskPromptContext.empty();
        data.setProjectSpaceName("test-space");
        data.setQuestion("如何启动?");
        String result = manager.renderTaskTemplate("self-learning-insight", TaskPromptContext.class, data);
        assertThat(result).contains("test-space").contains("如何启动?");
    }

    @Test
    void renderTaskTemplate_fileMode_usesClasspathTemplate() {
        PromptManager manager = new PromptManager(agentConfigService, loader, false);
        TaskPromptContext data = TaskPromptContext.empty();
        data.setProjectSpaceName("test-space");
        data.setQuestion("如何启动?");
        String result = manager.renderTaskTemplate("self-learning-insight", TaskPromptContext.class, data);
        assertThat(result).isNotBlank();
    }

    // --- classpathSystemPath ---

    @Test
    void classpathSystemPath_knownAgentId_returnsPath() {
        PromptManager manager = new PromptManager(agentConfigService, loader, false);
        assertThat(manager.classpathSystemPath("self-learning-insight"))
                .isEqualTo("prompts/self-learning-insight-system.md");
        assertThat(manager.classpathSystemPath("self-learning-insight-review"))
                .isEqualTo("prompts/self-learning-insight-review-verify-system.md");
        assertThat(manager.classpathSystemPath("self-learning-insight-refine"))
                .isEqualTo("prompts/self-learning-insight-refine-system.md");
    }

    @Test
    void classpathSystemPath_unknownAgentId_returnsNull() {
        PromptManager manager = new PromptManager(agentConfigService, loader, false);
        assertThat(manager.classpathSystemPath("code-researcher")).isNull();
        assertThat(manager.classpathSystemPath("orchestrator")).isNull();
    }

    // --- classpathTaskPath ---

    @Test
    void classpathTaskPath_knownAgentId_returnsPath() {
        PromptManager manager = new PromptManager(agentConfigService, loader, false);
        assertThat(manager.classpathTaskPath("self-learning-insight"))
                .isEqualTo("prompts/self-learning-insight-task.md");
        assertThat(manager.classpathTaskPath("self-learning-insight-review"))
                .isEqualTo("prompts/self-learning-insight-review-verify-task.md");
        assertThat(manager.classpathTaskPath("self-learning-insight-refine"))
                .isEqualTo("prompts/self-learning-insight-refine-task.md");
    }

    @Test
    void classpathTaskPath_unknownAgentId_returnsNull() {
        PromptManager manager = new PromptManager(agentConfigService, loader, false);
        assertThat(manager.classpathTaskPath("code-researcher")).isNull();
        assertThat(manager.classpathTaskPath("orchestrator")).isNull();
    }

    // --- getSkillContent ---

    @Test
    void getSkillContent_loadsFromClasspath() {
        PromptManager manager = new PromptManager(agentConfigService, loader, false);
        String result = manager.getSkillContent("spring_boot_analysis");
        assertThat(result).containsIgnoringCase("Spring Boot");
    }
}
