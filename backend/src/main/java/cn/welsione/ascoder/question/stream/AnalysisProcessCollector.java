package cn.welsione.ascoder.question.stream;

import cn.welsione.ascoder.agent.domain.AgentStreamEvent;
import cn.welsione.ascoder.agent.domain.AgentStreamEventType;
import cn.welsione.ascoder.agent.domain.AgentStreamSource;
import cn.welsione.ascoder.agent.domain.AgentToolCall;
import cn.welsione.ascoder.agent.domain.AgentToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 收集流式 Agent 事件，生成可持久化和回放的分析过程文本。
 */
class AnalysisProcessCollector {

    private static final int MAX_CHARS = 60000;

    private final Map<String, AgentProcess> processes = new LinkedHashMap<>();
    private int size;

    void append(AgentStreamEvent event) {
        if (event == null || size >= MAX_CHARS) {
            return;
        }
        AgentProcess process = processFor(event);
        appendToolCalls(process, event);
        appendToolResults(process, event);

        String content = event.contentOrEmpty();
        if (content.isBlank()) {
            return;
        }
        if (event.getType() == AgentStreamEventType.HANDOFF) {
            process.append("Agent 交接：" + content);
        } else if (event.getType() == AgentStreamEventType.REASONING) {
            process.append(content);
        } else if (event.getType() == AgentStreamEventType.HINT) {
            process.append("提示：" + content);
        } else if (event.getType() == AgentStreamEventType.SUMMARY) {
            process.append("阶段摘要：" + content);
        } else if (event.getType() == AgentStreamEventType.AGENT_RESULT && !isOrchestrator(process)) {
            process.append("Agent 结论：" + content);
        } else if (event.getType() == AgentStreamEventType.EVENT) {
            process.append(content);
        }
    }

    String toMarkdown() {
        if (processes.isEmpty()) {
            return null;
        }
        StringBuilder markdown = new StringBuilder();
        for (AgentProcess process : processes.values()) {
            String content = process.content.toString().trim();
            if (content.isBlank()) {
                continue;
            }
            if (!markdown.isEmpty()) {
                markdown.append("\n\n");
            }
            markdown.append("### ").append(process.name).append("\n\n").append(content);
        }
        return markdown.isEmpty() ? null : markdown.toString();
    }

    private AgentProcess processFor(AgentStreamEvent event) {
        AgentStreamSource source = event.getSource();
        String id = source == null || source.getAgentId() == null ? "orchestrator" : source.getAgentId();
        String name = source == null || source.getAgentName() == null ? agentLabel(id) : source.getAgentName();
        return processes.computeIfAbsent(id, key -> new AgentProcess(id, name));
    }

    private void appendToolCalls(AgentProcess process, AgentStreamEvent event) {
        if (!event.isLast() || event.getToolCalls().isEmpty()) {
            return;
        }
        for (AgentToolCall call : event.getToolCalls()) {
            String input = call.getInput() == null ? "" : "\n\n```json\n" + call.getInput() + "\n```";
            process.append("工具调用 `" + call.getName() + "`" + input);
        }
    }

    private void appendToolResults(AgentProcess process, AgentStreamEvent event) {
        if (event.getToolResults().isEmpty()) {
            return;
        }
        for (AgentToolResult result : event.getToolResults()) {
            String output = result.getOutput() == null ? "" : "\n\n" + result.getOutput();
            process.append("工具结果 `" + result.getName() + "`" + output);
        }
    }

    private void append(StringBuilder builder, String content) {
        if (content == null || content.isBlank() || size >= MAX_CHARS) {
            return;
        }
        String normalized = content.trim();
        int remaining = MAX_CHARS - size;
        String value = normalized.length() > remaining ? normalized.substring(0, remaining) : normalized;
        if (!builder.isEmpty()) {
            builder.append("\n\n");
            size += 2;
        }
        builder.append(value);
        size += value.length();
    }

    private boolean isOrchestrator(AgentProcess process) {
        return "orchestrator".equals(process.id);
    }

    private String agentLabel(String agentId) {
        if ("orchestrator".equals(agentId)) {
            return "Ascoder";
        }
        if ("code-researcher".equals(agentId)) {
            return "Code Researcher";
        }
        if ("impact-analyzer".equals(agentId)) {
            return "Impact Analyzer";
        }
        if ("self-learning".equals(agentId)) {
            return "Self Learning Agent";
        }
        if ("product-manager".equals(agentId)) {
            return "Product Manager Agent";
        }
        if ("test-manager".equals(agentId)) {
            return "Test Manager Agent";
        }
        return agentId;
    }

    private class AgentProcess {
        private final String id;
        private final String name;
        private final StringBuilder content = new StringBuilder();

        private AgentProcess(String id, String name) {
            this.id = id;
            this.name = name;
        }

        private void append(String content) {
            AnalysisProcessCollector.this.append(this.content, content);
        }
    }
}
