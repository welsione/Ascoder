package cn.welsione.ascoder.agent.persistence;

import cn.welsione.ascoder.agent.domain.AgentRunRecord;
import cn.welsione.ascoder.agent.domain.AgentRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AgentRunRecord JPA 仓库，提供按 agentId 分页历史、按 questionId 查询、按中断状态查询。
 */
public interface AgentRunRecordJpaRepository extends JpaRepository<AgentRunRecord, Long> {

    Page<AgentRunRecord> findByAgentIdOrderByStartedAtDesc(String agentId, Pageable pageable);

    List<AgentRunRecord> findByQuestionIdOrderByStartedAtAsc(Long questionId);

    List<AgentRunRecord> findByStatusAndFinishedAtIsNull(AgentRunStatus status);

    long countByAgentId(String agentId);
}
