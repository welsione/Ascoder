package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 候选洞察仓库，支持项目空间级审核流转。
 */
public interface LearningInsightJpaRepository extends JpaRepository<LearningInsight, Long> {
    List<LearningInsight> findByProjectSpace_IdOrderByUpdatedAtDesc(Long projectSpaceId);

    Optional<LearningInsight> findByIdAndProjectSpace_Id(Long id, Long projectSpaceId);

    long countByProjectSpace_IdAndStatus(Long projectSpaceId, LearningInsightStatus status);

    /**
     * 批量解除对指定仓库的引用，用于仓库删除时清理。
     */
    @Modifying
    @Query("update LearningInsight e set e.repository = null where e.repository.id = :repositoryId")
    int detachFromRepository(@Param("repositoryId") Long repositoryId);
}
