package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 经验知识仓库，提供项目空间范围内的经验查询。
 */
public interface LearningExperienceJpaRepository extends JpaRepository<LearningExperience, Long> {
    List<LearningExperience> findByProjectSpace_IdOrderByUpdatedAtDesc(Long projectSpaceId);

    Optional<LearningExperience> findByIdAndProjectSpace_Id(Long id, Long projectSpaceId);

    boolean existsBySourceQuestion_IdAndStatus(Long sourceQuestionId, LearningExperienceStatus status);

    /**
     * 批量解除对指定仓库的引用，用于仓库删除时清理。
     */
    @Modifying
    @Query("update LearningExperience e set e.repository = null where e.repository.id = :repositoryId")
    int detachFromRepository(@Param("repositoryId") Long repositoryId);
}
