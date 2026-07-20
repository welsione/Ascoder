package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.persistence.QueryPlanJpaRepository;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link QuestionQueryPort} 的默认实现，封装 question 聚合的只读查询，
 * 收口 persistence 层依赖，对外仅暴露端口方法。
 */
@Component
@RequiredArgsConstructor
public class QuestionQueryPortImpl implements QuestionQueryPort {

    private final QuestionJpaRepository questionRepository;
    private final QueryPlanJpaRepository queryPlanRepository;

    @Override
    public List<Question> findByProjectSpaceId(Long projectSpaceId) {
        return questionRepository.findByProjectSpaceIdOrderByCreatedAtAsc(projectSpaceId);
    }

    @Override
    public List<Question> findByConversationId(Long conversationId) {
        return questionRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Override
    public Optional<Question> findById(Long questionId) {
        return questionRepository.findById(questionId);
    }

    @Override
    public List<QueryPlan> findByQuestionIdIn(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return queryPlanRepository.findByQuestionIdIn(questionIds);
    }
}
