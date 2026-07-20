package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.question.persistence.ConversationJpaRepository;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听项目空间删除事件，解除 Question 和 Conversation 对已删除项目空间的引用。
 *
 * <p>替代 {@code ProjectSpaceService.delete} 中之前直接使用的跨聚合 JPQL。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSpaceDeletedListener {

    private final QuestionJpaRepository questionRepository;
    private final ConversationJpaRepository conversationRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onProjectSpaceDeleted(ProjectSpaceDeletedEvent event) {
        log.info("项目空间删除事件：解除 Question/Conversation 对 projectSpaceId={} 的引用", event.getProjectSpaceId());
        int questionsUnlinked = questionRepository.detachFromProjectSpace(event.getProjectSpaceId());
        int conversationsUnlinked = conversationRepository.detachFromProjectSpace(event.getProjectSpaceId());
        log.info("解绑完成：{} 条问题, {} 条会话", questionsUnlinked, conversationsUnlinked);
    }
}
