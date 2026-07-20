package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 经验知识仓库，提供项目空间范围内的经验查询。
 */
public interface LearningExperienceJpaRepository extends JpaRepository<LearningExperience, Long> {
    List<LearningExperience> findByProjectSpace_IdOrderByUpdatedAtDesc(Long projectSpaceId);

    Optional<LearningExperience> findByIdAndProjectSpace_Id(Long id, Long projectSpaceId);

    boolean existsBySourceQuestion_IdAndStatus(Long sourceQuestionId, LearningExperienceStatus status);
}
