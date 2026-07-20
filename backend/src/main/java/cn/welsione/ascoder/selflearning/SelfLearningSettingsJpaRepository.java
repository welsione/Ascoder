package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 自学习设置仓库，按项目空间定位唯一配置。
 */
public interface SelfLearningSettingsJpaRepository extends JpaRepository<SelfLearningSettings, Long> {
    Optional<SelfLearningSettings> findByProjectSpace_Id(Long projectSpaceId);
}
