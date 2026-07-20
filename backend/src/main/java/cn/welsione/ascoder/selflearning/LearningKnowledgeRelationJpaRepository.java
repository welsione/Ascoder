package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 知识关系仓库，维护业务概念、代码符号和知识项之间的关系。
 */
public interface LearningKnowledgeRelationJpaRepository extends JpaRepository<LearningKnowledgeRelation, Long> {
    List<LearningKnowledgeRelation> findByProjectSpace_IdOrderByUpdatedAtDesc(Long projectSpaceId);
}
