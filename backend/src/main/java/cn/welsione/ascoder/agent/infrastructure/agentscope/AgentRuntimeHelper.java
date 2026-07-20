package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import io.agentscope.core.agent.EventSource;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agent.EventType;

import java.nio.file.Path;

/**
 * Agent 运行时上下文、EventSource、StreamOptions 的构建工具。
 */
class AgentRuntimeHelper {

    private static final String ORCHESTRATOR = "orchestrator";

    private AgentRuntimeHelper() {
    }

    static RuntimeContext runtimeContext(AgentRequest request) {
        return RuntimeContext.builder()
                .sessionId(sessionId(request))
                .userId("project-space-" + request.getProjectSpaceId())
                .put(AgentRequestContext.class, AgentRequestContext.from(request))
                .build();
    }

    static RuntimeContext childRuntimeContext(AgentRequest request, String agentId) {
        return RuntimeContext.builder()
                .sessionId(sessionId(request) + "/" + agentId)
                .userId("project-space-" + request.getProjectSpaceId())
                .put(AgentRequestContext.class, AgentRequestContext.childFrom(request, sessionId(request)))
                .build();
    }

    static EventSource agentSource(AgentRequest request, String agentId, String agentName, int depth) {
        return EventSource.builder()
                .agentId(agentId)
                .agentName(agentName)
                .sessionId(depth == 0 ? sessionId(request) : sessionId(request) + "/" + agentId)
                .parentSessionId(depth == 0 ? null : sessionId(request))
                .depth(depth)
                .path(depth == 0 ? ORCHESTRATOR : ORCHESTRATOR + "/" + agentId)
                .build();
    }

    static StreamOptions streamOptions() {
        return StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT, EventType.HINT, EventType.SUMMARY, EventType.AGENT_RESULT)
                .includeReasoningResult(true)
                .includeActingChunk(false)
                .includeSummaryResult(true)
                .build();
    }

    static Path harnessWorkspace(AgentRequest request) {
        return Path.of(request.getProjectSpaceRootPath()).resolve(".ascoder-harness");
    }

    /**
     * SPECIALIST 角色的 workspace 路径，位于项目空间下的 .ascoder-specialist 子目录。
     */
    static Path specialistWorkspace(AgentRequest request) {
        return Path.of(request.getProjectSpaceRootPath()).resolve(".ascoder-specialist");
    }

    static String sessionId(AgentRequest request) {
        if (request.getConversationId() != null) {
            return "conversation-" + request.getConversationId();
        }
        return "project-space-" + request.getProjectSpaceId();
    }

    static String firstNonBlankLine(String text) {
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("已生成回答");
    }
}
