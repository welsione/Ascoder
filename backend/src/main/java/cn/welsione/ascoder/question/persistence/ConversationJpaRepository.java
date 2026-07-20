package cn.welsione.ascoder.question.persistence;

import cn.welsione.ascoder.question.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 会话实体的 JPA 仓库。 */
public interface ConversationJpaRepository extends JpaRepository<Conversation, Long> {

    /**
     * 批量解除对指定项目空间的引用，用于项目空间删除时清理。
     */
    @Modifying
    @Query("update Conversation c set c.projectSpaceId = null where c.projectSpaceId = :projectSpaceId")
    int detachFromProjectSpace(@Param("projectSpaceId") Long projectSpaceId);
}
