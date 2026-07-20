package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.question.domain.Conversation;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import cn.welsione.ascoder.question.persistence.ConversationJpaRepository;
import cn.welsione.ascoder.question.persistence.QueryPlanJpaRepository;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.util.List;

/**
 * 会话服务，负责会话级别的业务操作。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationJpaRepository conversationRepository;
    private final QuestionJpaRepository questionRepository;
    private final QueryPlanJpaRepository queryPlanRepository;
    private final EntityManager entityManager;

    /**
     * 删除会话及其关联的所有问题和查询规划。
     */
    @Transactional
    public void delete(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在: " + conversationId));

        List<Question> questions = questionRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        boolean hasRunning = questions.stream().anyMatch(q -> q.getStatus() == QuestionStatus.RUNNING);
        if (hasRunning) {
            throw new InvalidStateException("会话中存在正在处理的问题，请等待完成后再删除");
        }

        List<Long> questionIds = questions.stream().map(Question::getId).toList();
        if (!questionIds.isEmpty()) {
            queryPlanRepository.deleteByQuestionIdIn(questionIds);
            entityManager.flush();
        }
        questionRepository.deleteByConversationId(conversationId);

        conversationRepository.delete(conversation);
        log.info("已删除会话 {} 及其 {} 个问题", conversationId, questions.size());
    }
}
