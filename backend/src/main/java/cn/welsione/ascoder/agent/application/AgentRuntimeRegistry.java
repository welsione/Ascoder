package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentRunStatus;
import cn.welsione.ascoder.agent.domain.AgentRuntimeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent 运行态注册表，维护每个 Agent 当前的状态（IDLE / RUNNING）。
 *
 * <p>纯内存态（{@link ConcurrentHashMap}），进程重启后清空。状态本身不落库，
 * 运行结束写入 {@code agentRunRecords}（持久化历史）。markRunning / markIdle 时
 * 发布 {@link AgentStatusChangedEvent}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntimeRegistry {

    private final Map<String, AgentRuntimeState> states = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 标记 Agent 为运行中，发布 RUNNING 事件。
     */
    public void markRunning(String agentId, Long questionId, Long runRecordId) {
        AgentRuntimeState state = new AgentRuntimeState(agentId, AgentRuntimeStatus.RUNNING,
                questionId, runRecordId, LocalDateTime.now());
        states.put(agentId, state);
        log.info("Agent 进入运行态，agentId={}，questionId={}，runRecordId={}", agentId, questionId, runRecordId);
        eventPublisher.publishEvent(new AgentStatusChangedEvent(agentId, AgentRuntimeStatus.RUNNING, runRecordId));
    }

    /**
     * 标记 Agent 为空闲，发布 IDLE 事件。finalStatus 用于日志记录运行最终结果。
     */
    public void markIdle(String agentId, Long runRecordId, AgentRunStatus finalStatus) {
        states.remove(agentId);
        log.info("Agent 进入空闲态，agentId={}，runRecordId={}，finalStatus={}", agentId, runRecordId, finalStatus);
        eventPublisher.publishEvent(new AgentStatusChangedEvent(agentId, AgentRuntimeStatus.IDLE, runRecordId));
    }

    /**
     * 查询 Agent 当前状态，未记录则 IDLE。
     */
    public AgentRuntimeStatus status(String agentId) {
        AgentRuntimeState state = states.get(agentId);
        return state != null ? state.getStatus() : AgentRuntimeStatus.IDLE;
    }

    /**
     * 全量快照，供面板列表展示。
     */
    public List<AgentRuntimeView> snapshot() {
        return states.values().stream()
                .map(state -> new AgentRuntimeView(state.getAgentId(), state.getStatus(),
                        state.getQuestionId(), state.getRunRecordId(), state.getStartedAt()))
                .collect(Collectors.toList());
    }
}
