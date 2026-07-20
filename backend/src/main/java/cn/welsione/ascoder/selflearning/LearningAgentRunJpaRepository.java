package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Self Learning Agent 后台整理运行记录仓库。
 */
public interface LearningAgentRunJpaRepository extends JpaRepository<LearningAgentRun, Long> {
    List<LearningAgentRun> findTop20ByProjectSpace_IdOrderByCreatedAtDesc(Long projectSpaceId);
}
