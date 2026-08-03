package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.question.persistence.ConversationJpaRepository;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import cn.welsione.ascoder.repository.RepositoryDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听仓库删除事件，解除 Question 和 Conversation 对已删除仓库的引用。
 *
 * <p>questions / conversations 的 repositoryId 为 nullable 外键，删除仓库时将引用置 null，
 * 保留历史问答与会话数据，不阻断仓库删除。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepositoryDeletedListener {

    private final QuestionJpaRepository questionRepository;
    private final ConversationJpaRepository conversationRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onRepositoryDeleted(RepositoryDeletedEvent event) {
        log.info("仓库删除事件：解除 Question/Conversation 对 repositoryId={} 的引用", event.getRepositoryId());
        int questionsUnlinked = questionRepository.detachFromRepository(event.getRepositoryId());
        int conversationsUnlinked = conversationRepository.detachFromRepository(event.getRepositoryId());
        log.info("解绑完成：{} 条问题, {} 条会话", questionsUnlinked, conversationsUnlinked);
    }
}
