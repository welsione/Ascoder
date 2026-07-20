package cn.welsione.ascoder.question.persistence;

import cn.welsione.ascoder.question.domain.AgentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Agent 事件 JPA 仓库接口。
 */
public interface AgentEventJpaRepository extends JpaRepository<AgentEvent, Long> {

    List<AgentEvent> findByQuestionIdAndAttemptNoOrderBySequenceNoAsc(Long questionId, int attemptNo);

    @Query("select coalesce(max(e.attemptNo), 0) from AgentEvent e where e.questionId = :questionId")
    int maxAttemptNoByQuestionId(@Param("questionId") Long questionId);

    int countByQuestionIdAndAttemptNo(Long questionId, int attemptNo);

    void deleteByQuestionId(Long questionId);
}
