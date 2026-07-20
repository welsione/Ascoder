package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 候选洞察仓库，支持项目空间级审核流转。
 */
public interface LearningInsightJpaRepository extends JpaRepository<LearningInsight, Long> {
    List<LearningInsight> findByProjectSpace_IdOrderByUpdatedAtDesc(Long projectSpaceId);

    Optional<LearningInsight> findByIdAndProjectSpace_Id(Long id, Long projectSpaceId);

    long countByProjectSpace_IdAndStatus(Long projectSpaceId, LearningInsightStatus status);
}
