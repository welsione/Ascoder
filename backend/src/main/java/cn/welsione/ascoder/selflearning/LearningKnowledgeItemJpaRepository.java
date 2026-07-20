package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 正式知识仓库，提供审核后知识的管理和召回。
 */
public interface LearningKnowledgeItemJpaRepository extends JpaRepository<LearningKnowledgeItem, Long> {
    List<LearningKnowledgeItem> findByProjectSpace_IdOrderByUpdatedAtDesc(Long projectSpaceId);

    Optional<LearningKnowledgeItem> findByIdAndProjectSpace_Id(Long id, Long projectSpaceId);
}
