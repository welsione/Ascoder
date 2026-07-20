package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Agent 运行时业务上下文，封装需要通过 RuntimeContext 传递给框架的业务标识。
 */
@Data
@AllArgsConstructor
public class AgentRequestContext {

    private Long projectSpaceId;
    private Long conversationId;
    private String parentSessionId;

    public static AgentRequestContext from(AgentRequest request) {
        return new AgentRequestContext(request.getProjectSpaceId(), request.getConversationId(), null);
    }

    public static AgentRequestContext childFrom(AgentRequest request, String parentSessionId) {
        return new AgentRequestContext(request.getProjectSpaceId(), request.getConversationId(), parentSessionId);
    }
}
