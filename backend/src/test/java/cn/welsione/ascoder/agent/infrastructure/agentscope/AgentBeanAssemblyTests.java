package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.ascoder.agent.application.AgentRunRecordService;
import cn.welsione.ascoder.agent.application.AgentRuntimeRegistry;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import cn.welsione.ascoder.agent.port.ChatModelFactory;
import cn.welsione.ascoder.agent.port.CodeAnswerAgent;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.question.planning.QuestionPlanningAgent;
import cn.welsione.ascoder.selflearning.RuleBasedSelfLearningInsightAgent;
import cn.welsione.ascoder.selflearning.SelfLearningInsightAgent;
import cn.welsione.ascoder.selflearning.SelfLearningInsightReviewAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 验证 ascoder.llm-provider 不同模式下关键 Agent Bean 的条件装配，防止装配回归。
 *
 * <p>历史遗留的 provider 条件误绑已修复：
 * <ul>
 *   <li>database 模式（默认）装配 RuleBasedSelfLearningInsightAgent（不调 LLM 的兜底空实现）；</li>
 *   <li>agentscope 模式装配 AgentScopeSelfLearningInsightAgent（调 LLM 生成洞察）。</li>
 * </ul>
 * 两种模式下 CodeAnswerAgent / QuestionPlanningAgent / SelfLearningInsightReviewAgent 都必须存在
 * （它们依赖 provider 中立的 ChatModelFactory port，已移除错误的 @ConditionalOnProperty 绑定）。</p>
 *
 * <p>使用 ApplicationContextRunner 轻量切片测试，通过 MocksConfiguration 提供 Agent 构造器依赖，
 * 不拉起 JPA / 数据库等完整上下文。ChatModelFactory 用 mock 提供（本测试聚焦 Agent bean 装配，
 * ModelFactory 的互斥装配由其各自的 @ConditionalOnProperty 保证）。</p>
 */
class AgentBeanAssemblyTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(MocksConfiguration.class)
            .withConfiguration(UserConfigurations.of(
                    AgentScopeCodeAnswerAgent.class,
                    AgentScopeQuestionPlanningAgent.class,
                    AgentScopeSelfLearningInsightReviewAgent.class,
                    AgentScopeSelfLearningInsightAgent.class,
                    RuleBasedSelfLearningInsightAgent.class
            ));

    @Test
    void databaseMode_shouldAssembleAllAgentBeansWithRuleBasedInsight() {
        runner.withPropertyValues("ascoder.llm-provider=database")
                .run(context -> {
                    assertThat(context).hasSingleBean(CodeAnswerAgent.class);
                    assertThat(context).hasSingleBean(QuestionPlanningAgent.class);
                    assertThat(context).hasSingleBean(SelfLearningInsightReviewAgent.class);
                    assertThat(context).hasSingleBean(SelfLearningInsightAgent.class);
                    assertThat(context).getBean(SelfLearningInsightAgent.class)
                            .isInstanceOf(RuleBasedSelfLearningInsightAgent.class);
                });
    }

    @Test
    void agentscopeMode_shouldAssembleAllAgentBeansWithAgentScopeInsight() {
        runner.withPropertyValues("ascoder.llm-provider=agentscope")
                .run(context -> {
                    assertThat(context).hasSingleBean(CodeAnswerAgent.class);
                    assertThat(context).hasSingleBean(QuestionPlanningAgent.class);
                    assertThat(context).hasSingleBean(SelfLearningInsightReviewAgent.class);
                    assertThat(context).hasSingleBean(SelfLearningInsightAgent.class);
                    assertThat(context).getBean(SelfLearningInsightAgent.class)
                            .isInstanceOf(AgentScopeSelfLearningInsightAgent.class);
                });
    }

    /**
     * 为 Agent 构造器注入提供 mock 依赖，避免拉起完整 Spring 上下文。
     */
    @Configuration
    static class MocksConfiguration {
        @Bean
        AgentConfigService agentConfigService() {
            return mock(AgentConfigService.class);
        }

        @Bean
        ChatModelFactory chatModelFactory() {
            return mock(ChatModelFactory.class);
        }

        @Bean
        ConfigDrivenAgentFactory configDrivenAgentFactory() {
            return mock(ConfigDrivenAgentFactory.class);
        }

        @Bean
        ConfigDrivenToolingBuilder configDrivenToolingBuilder() {
            return mock(ConfigDrivenToolingBuilder.class);
        }

        @Bean
        SpecialistTaskAssembler specialistTaskAssembler() {
            return mock(SpecialistTaskAssembler.class);
        }

        @Bean
        AgentRunRecordService agentRunRecordService() {
            return mock(AgentRunRecordService.class);
        }

        @Bean
        AgentRuntimeRegistry agentRuntimeRegistry() {
            return mock(AgentRuntimeRegistry.class);
        }

        @Bean
        PromptManager promptManager() {
            return mock(PromptManager.class);
        }

        @Bean
        CodeGraphClient codeGraphClient() {
            return mock(CodeGraphClient.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
