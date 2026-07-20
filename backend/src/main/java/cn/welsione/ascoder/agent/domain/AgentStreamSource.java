package cn.welsione.ascoder.agent.domain;

import lombok.Value;

/**
 * Agent 流式事件来源，描述事件由哪个编排节点产生。
 */
@Value
public class AgentStreamSource {
    String agentId;
    String agentName;
    String agentKey;
    String sessionId;
    String parentSessionId;
    String taskId;
    int depth;
    String path;
}
