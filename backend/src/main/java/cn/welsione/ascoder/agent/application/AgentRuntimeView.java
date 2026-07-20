package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentRuntimeStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 运行态对外快照视图，供前端观测面板列表展示。
 */
@Data
@AllArgsConstructor
public class AgentRuntimeView {
    private String agentId;
    private AgentRuntimeStatus status;
    private Long questionId;
    private Long runRecordId;
    private LocalDateTime startedAt;
}
