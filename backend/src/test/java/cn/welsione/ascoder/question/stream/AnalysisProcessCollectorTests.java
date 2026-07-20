package cn.welsione.ascoder.question.stream;

import cn.welsione.ascoder.agent.domain.AgentStreamEvent;
import cn.welsione.ascoder.agent.domain.AgentStreamEventType;
import cn.welsione.ascoder.agent.domain.AgentStreamSource;
import cn.welsione.ascoder.agent.domain.AgentToolCall;
import cn.welsione.ascoder.agent.domain.AgentToolResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证流式分析过程收集器可将 Agent 事件整理成历史回放文本。
 */
class AnalysisProcessCollectorTests {

    @Test
    void shouldBuildMarkdownFromReasoningAndTools() {
        AnalysisProcessCollector collector = new AnalysisProcessCollector();
        AgentStreamSource source = new AgentStreamSource(
                "code-researcher", "Code Researcher", null, null, null, null, 1, "code-researcher"
        );

        collector.append(AgentStreamEvent.builder()
                .type(AgentStreamEventType.REASONING)
                .source(source)
                .content("先定位入口。")
                .build());
        collector.append(AgentStreamEvent.builder()
                .type(AgentStreamEventType.REASONING)
                .source(source)
                .last(true)
                .toolCall(new AgentToolCall("1", "codegraph_context", "{\"task\":\"chat\"}"))
                .build());
        collector.append(AgentStreamEvent.builder()
                .type(AgentStreamEventType.TOOL_RESULT)
                .source(source)
                .toolResult(new AgentToolResult("1", "codegraph_context", "找到 Thread.vue。", false))
                .build());

        String markdown = collector.toMarkdown();

        assertThat(markdown)
                .contains("### Code Researcher")
                .contains("先定位入口。")
                .contains("工具调用 `codegraph_context`")
                .contains("工具结果 `codegraph_context`")
                .contains("找到 Thread.vue。");
    }

    @Test
    void shouldReturnNullWhenNoVisibleContentCollected() {
        AnalysisProcessCollector collector = new AnalysisProcessCollector();

        assertThat(collector.toMarkdown()).isNull();
    }

    @Test
    void shouldPersistSelfLearningAgentActivityForReplay() {
        AnalysisProcessCollector collector = new AnalysisProcessCollector();
        AgentStreamSource source = new AgentStreamSource(
                "self-learning", "Self Learning Agent", null, null, null, null, 1, "orchestrator/self-learning"
        );

        collector.append(AgentStreamEvent.builder()
                .type(AgentStreamEventType.REASONING)
                .source(source)
                .content("正在检索项目空间正式知识。")
                .build());
        collector.append(AgentStreamEvent.builder()
                .type(AgentStreamEventType.SUMMARY)
                .source(source)
                .content("正式知识：支付入口由 PaymentService 负责。")
                .build());

        String markdown = collector.toMarkdown();

        assertThat(markdown)
                .contains("### Self Learning Agent")
                .contains("正在检索项目空间正式知识。")
                .contains("阶段摘要：正式知识：支付入口由 PaymentService 负责。");
    }

    @Test
    void shouldLimitCollectedProcessContent() {
        AnalysisProcessCollector collector = new AnalysisProcessCollector();
        AgentStreamSource source = new AgentStreamSource(
                "code-researcher", "Code Researcher", null, null, null, null, 1, "code-researcher"
        );

        collector.append(AgentStreamEvent.builder()
                .type(AgentStreamEventType.TOOL_RESULT)
                .source(source)
                .toolResult(new AgentToolResult("1", "codegraph_context", "中".repeat(70000), false))
                .build());

        String markdown = collector.toMarkdown();

        assertThat(markdown).startsWith("### Code Researcher");
        assertThat(markdown.length()).isLessThan(60100);
    }
}
