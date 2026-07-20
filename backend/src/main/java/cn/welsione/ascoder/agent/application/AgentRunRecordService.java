package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentRunRecord;
import cn.welsione.ascoder.agent.domain.AgentRunStatus;
import cn.welsione.ascoder.agent.persistence.AgentRunRecordJpaRepository;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 运行记录服务，负责运行记录的创建、完成、查询与启动时中断修复。
 *
 * <p>仅写 {@code agentRunRecords} 表，不注入 chat 聚合的 Repository（跨聚合边界），
 * questionId / conversationId 由调用方（编排流程）传入。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunRecordService {

    private final AgentRunRecordJpaRepository repository;

    /**
     * 创建运行中记录，返回带 id 的实体。
     */
    @Transactional
    public AgentRunRecord start(String agentId, Long agentConfigId, Long questionId, Long conversationId,
                                int attemptNo, String inputSummary) {
        AgentRunRecord record = new AgentRunRecord();
        record.setAgentId(agentId);
        record.setAgentConfigId(agentConfigId);
        record.setQuestionId(questionId);
        record.setConversationId(conversationId);
        record.setAttemptNo(attemptNo);
        record.setStatus(AgentRunStatus.RUNNING);
        record.setInputSummary(inputSummary);
        record.setStartedAt(LocalDateTime.now());
        return repository.save(record);
    }

    /**
     * 完成运行记录，更新状态、输出摘要与耗时。
     */
    @Transactional
    public void finish(Long runRecordId, AgentRunStatus status, String outputSummary,
                       int toolCallCount, int iterCount, String errorMessage) {
        AgentRunRecord record = get(runRecordId);
        record.setStatus(status);
        record.setOutputSummary(outputSummary);
        record.setToolCallCount(toolCallCount);
        record.setIterCount(iterCount);
        record.setErrorMessage(errorMessage);
        record.setFinishedAt(LocalDateTime.now());
        if (record.getStartedAt() != null) {
            record.setDurationMs(Duration.between(record.getStartedAt(), record.getFinishedAt()).toMillis());
        }
        repository.save(record);
    }

    @Transactional(readOnly = true)
    public Page<AgentRunRecord> listByAgent(String agentId, Pageable pageable) {
        return repository.findByAgentIdOrderByStartedAtDesc(agentId, pageable);
    }

    @Transactional(readOnly = true)
    public long countByAgent(String agentId) {
        return repository.countByAgentId(agentId);
    }

    @Transactional(readOnly = true)
    public AgentRunRecord get(Long runId) {
        return repository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("AgentRunRecord", runId));
    }

    /**
     * 启动时将残留的 RUNNING（finishedAt 为空）记录批量改为 INTERRUPTED（D5 兜底）。
     */
    @PostConstruct
    @Transactional
    public void repairInterrupted() {
        List<AgentRunRecord> dangling = repository.findByStatusAndFinishedAtIsNull(AgentRunStatus.RUNNING);
        if (dangling.isEmpty()) {
            return;
        }
        log.warn("发现 {} 条中断的 Agent 运行记录，修正为 INTERRUPTED", dangling.size());
        for (AgentRunRecord record : dangling) {
            record.setStatus(AgentRunStatus.INTERRUPTED);
            record.setErrorMessage("进程重启前未正常结束，已修正为中断状态");
            record.setFinishedAt(LocalDateTime.now());
        }
        repository.saveAll(dangling);
    }
}
