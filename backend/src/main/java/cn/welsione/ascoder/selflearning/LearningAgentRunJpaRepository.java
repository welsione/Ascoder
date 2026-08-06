package cn.welsione.ascoder.selflearning;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Self Learning Agent 后台整理运行记录仓库。
 */
public interface LearningAgentRunJpaRepository extends JpaRepository<LearningAgentRun, Long> {
    List<LearningAgentRun> findTop20ByProjectSpace_IdOrderByCreatedAtDesc(Long projectSpaceId);

    /**
     * 分页查询指定状态的运行记录（启动恢复使用，分页限制一次性载入范围）。
     */
    List<LearningAgentRun> findByStatusIn(Collection<LearningAgentRunStatus> statuses, Pageable pageable);
}
