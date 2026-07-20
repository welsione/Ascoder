package cn.welsione.ascoder.agent.extension.skill;

import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.jprompt.loader.ClasspathTemplateLoader;
import cn.welsione.jprompt.loader.CompositeTemplateLoader;
import cn.welsione.jprompt.loader.TemplateLoader;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuiltinSkillCatalogTests {

    private PromptManager createPromptManager() {
        AgentConfigService agentConfigService = mock(AgentConfigService.class);
        when(agentConfigService.getByAgentId("self-learning-insight")).thenReturn(Optional.empty());
        TemplateLoader noopFallback = path -> { throw new cn.welsione.jprompt.TemplateException("No fallback: " + path); };
        CompositeTemplateLoader loader = new CompositeTemplateLoader(new ClasspathTemplateLoader(), noopFallback);
        return new PromptManager(agentConfigService, loader, false);
    }

    @Test
    void providesCoreCodeAnalysisSkills() {
        PromptManager promptManager = createPromptManager();
        assertThat(BuiltinSkillCatalog.all(promptManager))
                .extracting("name")
                .contains(
                        "spring_boot_analysis",
                        "maven_project_analysis",
                        "vue_analysis",
                        "code_review_analysis",
                        "bug_root_cause_analysis",
                        "impact_analysis"
                );
        assertThat(BuiltinSkillCatalog.all(promptManager))
                .extracting("skillContent")
                .allSatisfy(content -> assertThat((String) content)
                        .startsWith("# ")
                        .contains("推荐 CodeGraph 工具顺序"));
    }
}
