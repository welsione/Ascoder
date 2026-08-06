package cn.welsione.ascoder.selflearning;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 自学习原始记录仓库，按项目空间读取交互和证据留痕。
 */
public interface LearningRawEventJpaRepository extends JpaRepository<LearningRawEvent, Long> {
    List<LearningRawEvent> findByProjectSpace_IdOrderByCreatedAtDesc(Long projectSpaceId);

    List<LearningRawEvent> findByProjectSpace_IdOrderByCreatedAtDesc(Long projectSpaceId, Pageable pageable);

    /**
     * 批量解除对指定仓库的引用，用于仓库删除时清理。
     */
    @Modifying
    @Query("update LearningRawEvent e set e.repository = null where e.repository.id = :repositoryId")
    int detachFromRepository(@Param("repositoryId") Long repositoryId);
}
