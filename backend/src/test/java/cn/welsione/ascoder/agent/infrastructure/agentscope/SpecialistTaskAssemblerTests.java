package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import cn.welsione.ascoder.agent.infrastructure.prompt.TaskPromptContext;
import cn.welsione.ascoder.question.planning.QuestionPlan;
import cn.welsione.ascoder.question.planning.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SpecialistTaskAssembler 任务模板渲染与上下文组装测试。
 *
 * <p>测试 buildContext 字段映射及 taskPrompt / synthesisPrompt 委托 PromptManager 的行为。</p>
 */
class SpecialistTaskAssemblerTests {

    private PromptManager promptManager;
    private SpecialistTaskAssembler assembler;

    @BeforeEach
    void setUp() {
        promptManager = mock(PromptManager.class);
        assembler = new SpecialistTaskAssembler(promptManager);
    }

    @Test
    void taskPromptDelegatesToPromptManager() {
        AgentConfig config = agentConfig("项目空间: {{projectSpaceName}}");
        TaskPromptContext context = buildContext();
        when(promptManager.renderTaskTemplate(eq("test-agent"), eq(TaskPromptContext.class), eq(context)))
                .thenReturn("rendered task");

        String result = assembler.taskPrompt(config, context);

        assertEquals("rendered task", result);
        verify(promptManager).renderTaskTemplate("test-agent", TaskPromptContext.class, context);
    }

    @Test
    void synthesisPromptDelegatesToPromptManager() {
        AgentConfig config = agentConfig("汇总: {{answerStyleInstruction}}");
        TaskPromptContext context = buildContext();
        when(promptManager.renderTaskTemplate(eq("test-agent"), eq(TaskPromptContext.class), eq(context)))
                .thenReturn("rendered synthesis");

        String result = assembler.synthesisPrompt(config, context);

        assertEquals("rendered synthesis", result);
        verify(promptManager).renderTaskTemplate("test-agent", TaskPromptContext.class, context);
    }

    @Test
    void buildContextCopiesAllFields() {
        AgentRequest request = buildRequest(plan(), "自学习经验", null);
        when(promptManager.getAnswerStyle("developer")).thenReturn("请用技术语言回答");

        TaskPromptContext context = assembler.buildContext(request,
                plan().toPromptText(),
                plan().getType().name(),
                plan().getRecommendedTools(),
                plan().getRewrittenQueries(),
                plan().getRecommendedSkills(),
                plan().getReasoning());

        assertEquals("demo-space", context.getProjectSpaceName());
        assertEquals("用户问题", context.getQuestion());
        assertNotNull(context.getQueryPlanSummary());
        assertEquals("BUSINESS_FLOW", context.getQueryPlanType());
        assertEquals(List.of("codegraph_search"), context.getQueryPlanRecommendedTools());
        assertEquals("自学习经验", context.getSelfLearningContext());
        assertEquals("请用技术语言回答", context.getAnswerStyleInstruction());
        assertEquals("developer", context.getAnswerStyleRoleKey());
        assertNull(context.getResearchResult());
        assertNull(context.getImpactResult());
        assertNull(context.getSpecialistResults());
    }

    @Test
    void buildContextUsesDefaultsForNullPlan() {
        AgentRequest request = buildRequest(null, null, null);
        when(promptManager.getAnswerStyle("developer")).thenReturn("请用技术语言回答");

        TaskPromptContext context = assembler.buildContext(request,
                null, null, null, null, null, null);

        assertNull(context.getQueryPlanType());
        assertNull(context.getQueryPlanSummary());
        assertNull(context.getQueryPlanRecommendedTools());
    }

    // helpers

    private TaskPromptContext buildContext() {
        AgentRequest request = buildRequest(plan(), "自学习经验", null);
        when(promptManager.getAnswerStyle("developer")).thenReturn("请用技术语言回答");
        return assembler.buildContext(request,
                plan().toPromptText(),
                plan().getType().name(),
                plan().getRecommendedTools(),
                plan().getRewrittenQueries(),
                plan().getRecommendedSkills(),
                plan().getReasoning());
    }

    private AgentRequest buildRequest(QuestionPlan plan, String selfLearningContext, String codeContext) {
        return new AgentRequest(
                1L, null, 100L, "demo-space", "/tmp/demo", "/tmp/demo/.codegraph",
                List.of(new AgentRequest.RepositoryContext(1L, "backend", 10L, "main", "abc123", "/tmp/work", "/tmp/cg", "backend", true)),
                "用户问题", "developer", null, null, codeContext, plan, null, selfLearningContext);
    }

    private QuestionPlan plan() {
        return new QuestionPlan(
                QuestionType.BUSINESS_FLOW,
                List.of("搜索登录逻辑"),
                List.of("codegraph_search"),
                List.of("spring-boot"),
                "推测是业务流程查询",
                0.85,
                List.of("入口", "业务"),
                List.of()
        );
    }

    private AgentConfig agentConfig(String templateContent) {
        AgentConfig config = new AgentConfig();
        config.setAgentId("test-agent");
        config.setSystemPrompt("sys");
        config.setTaskTemplate(templateContent);
        config.setMaxIters(10);
        return config;
    }
}
