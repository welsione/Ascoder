package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentRuntimeStatus;
import lombok.Value;

/**
 * Agent 运行态变化事件，由 {@link AgentRuntimeRegistry} 在 markRunning / markIdle 时发布，
 * 供 Controller SSE 端点推送给前端观测面板。
 */
@Value
public class AgentStatusChangedEvent {
    String agentId;
    AgentRuntimeStatus status;
    Long runRecordId;
}
