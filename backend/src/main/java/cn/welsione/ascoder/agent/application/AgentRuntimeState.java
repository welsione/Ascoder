package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentRuntimeStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 运行态内部状态（内存对象，不持久化）。
 */
@Data
@AllArgsConstructor
public class AgentRuntimeState {
    private String agentId;
    private AgentRuntimeStatus status;
    private Long questionId;
    private Long runRecordId;
    private LocalDateTime startedAt;
}
