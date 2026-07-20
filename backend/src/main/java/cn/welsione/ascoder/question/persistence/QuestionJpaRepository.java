package cn.welsione.ascoder.question.persistence;

import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 问题 JPA 仓库接口。
 */
public interface QuestionJpaRepository extends JpaRepository<Question, Long> {

    List<Question> findTop50ByOrderByCreatedAtDesc();

    List<Question> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<Question> findByProjectSpaceIdOrderByCreatedAtAsc(Long projectSpaceId);

    List<Question> findTop6ByConversationIdAndStatusOrderByCreatedAtDesc(Long conversationId, QuestionStatus status);

    boolean existsByProjectSpaceIdAndStatus(Long projectSpaceId, QuestionStatus status);

    /**
     * 批量解除对指定项目空间的引用，用于项目空间删除时清理。
     */
    @Modifying
    @Query("update Question q set q.projectSpaceId = null where q.projectSpaceId = :projectSpaceId")
    int detachFromProjectSpace(@Param("projectSpaceId") Long projectSpaceId);

    /**
     * 删除指定会话下的所有问题。
     */
    @Modifying
    @Query("delete from Question q where q.conversation.id = :conversationId")
    int deleteByConversationId(@Param("conversationId") Long conversationId);
}
