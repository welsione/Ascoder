package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 自学习原始记录仓库，按项目空间读取交互和证据留痕。
 */
public interface LearningRawEventJpaRepository extends JpaRepository<LearningRawEvent, Long> {
    List<LearningRawEvent> findByProjectSpace_IdOrderByCreatedAtDesc(Long projectSpaceId);

    List<LearningRawEvent> findTop50ByProjectSpace_IdOrderByCreatedAtDesc(Long projectSpaceId);
}
