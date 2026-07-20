package cn.welsione.ascoder.question.stream;

import cn.welsione.ascoder.agent.domain.AgentStreamEvent;
import cn.welsione.ascoder.agent.domain.AgentStreamEventType;
import cn.welsione.ascoder.agent.domain.AgentStreamSource;
import cn.welsione.ascoder.agent.domain.AgentToolCall;
import cn.welsione.ascoder.agent.domain.AgentToolResult;
import cn.welsione.ascoder.question.application.AgentEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 事件分发与转换，将 Agent 流式事件转换为前端 SSE 事件格式，并持久化事件用于回放。
 */
@Component
@RequiredArgsConstructor
class AgentEventDispatcher {

    private final SseConnectionManager connectionManager;
    private final AgentEventService agentEventService;

    void dispatch(Long questionId, AgentStreamEvent event,
                  StringBuilder summaryBuffer,
                  AtomicReference<String> finalAnswerRef,
                  AtomicReference<String> codeContextRef) {
        try {
            String content = event.contentOrEmpty();

            AgentStreamEventType type = event.getType();
            if (type == AgentStreamEventType.HANDOFF) {
                if (!content.isEmpty()) {
                    sendAndPersist(questionId, "handoff", eventPayload(event, content, false));
                }
            } else if (type == AgentStreamEventType.REASONING) {
                sendToolCalls(questionId, event);
                if (!content.isEmpty()) {
                    sendAndPersist(questionId, "reasoning", eventPayload(event, content, event.isLast()));
                }
            } else if (type == AgentStreamEventType.TOOL_RESULT) {
                sendToolResults(questionId, event, content);
            } else if (type == AgentStreamEventType.HINT) {
                if (!content.isEmpty()) {
                    sendAndPersist(questionId, "hint", eventPayload(event, content, false));
                }
            } else if (type == AgentStreamEventType.SUMMARY) {
                if (!content.isEmpty()) {
                    replaceSummary(summaryBuffer, content, event.isLast());
                    sendAndPersist(questionId, "summary", eventPayload(event, content, event.isLast()));
                }
            } else if (type == AgentStreamEventType.AGENT_RESULT) {
                if (!content.isEmpty()) {
                    finalAnswerRef.set(content);
                    sendAndPersist(questionId, "result", eventPayload(event, content, true));
                }
            } else {
                sendAndPersist(questionId, "event", eventPayload(event, content, false));
            }
        } catch (Exception ex) {
            // 不中断流式处理，仅记录
        }
    }

    /**
     * 发送 SSE 事件并异步持久化，用于后续回放。
     */
    private void sendAndPersist(Long questionId, String eventName, Map<String, Object> payload) {
        connectionManager.sendEvent(questionId, eventName, payload);
        agentEventService.append(questionId, eventName, payload);
    }

    private void sendToolCalls(Long questionId, AgentStreamEvent event) {
        if (!event.isLast() || event.getToolCalls().isEmpty()) {
            return;
        }
        for (AgentToolCall call : event.getToolCalls()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", call.getId());
            payload.put("name", call.getName());
            payload.put("input", call.getInput());
            payload.put("last", event.isLast());
            appendSource(payload, event);
            sendAndPersist(questionId, "tool_call", payload);
        }
    }

    private void sendToolResults(Long questionId, AgentStreamEvent event, String fallbackContent) {
        if (event.getToolResults().isEmpty()) {
            sendAndPersist(questionId, "tool_result", eventPayload(event, fallbackContent, false));
            return;
        }
        for (AgentToolResult result : event.getToolResults()) {
            Map<String, Object> payload = eventPayload(event, result.getOutput(), false);
            payload.put("id", result.getId());
            payload.put("name", result.getName());
            payload.put("suspended", result.isSuspended());
            appendSource(payload, event);
            sendAndPersist(questionId, "tool_result", payload);
        }
    }

    Map<String, Object> eventPayload(AgentStreamEvent event, String content, boolean replace) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        payload.put("last", event.isLast());
        payload.put("replace", replace);
        payload.put("eventType", event.getType().name());
        if (event.getMessageId() != null) {
            payload.put("messageId", event.getMessageId());
        }
        appendSource(payload, event);
        return payload;
    }

    private void appendSource(Map<String, Object> payload, AgentStreamEvent event) {
        AgentStreamSource source = event.getSource();
        if (source == null) {
            payload.put("agentId", "orchestrator");
            payload.put("agentName", "Ascoder");
            payload.put("depth", 0);
            payload.put("path", "orchestrator");
            return;
        }
        payload.put("agentId", source.getAgentId() == null ? "subagent" : source.getAgentId());
        payload.put("agentName", source.getAgentName() == null ? source.getAgentId() : source.getAgentName());
        payload.put("agentKey", source.getAgentKey());
        payload.put("sessionId", source.getSessionId());
        payload.put("parentSessionId", source.getParentSessionId());
        payload.put("taskId", source.getTaskId());
        payload.put("depth", source.getDepth());
        payload.put("path", source.getPath());
    }

    static void replaceSummary(StringBuilder summaryBuffer, String content, boolean replace) {
        if (replace) {
            summaryBuffer.setLength(0);
        }
        summaryBuffer.append(content);
    }
}
