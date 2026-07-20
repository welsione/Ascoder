package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.question.domain.Question;

import java.util.List;
import java.util.Optional;

/**
 * 问题与查询规划只读查询出口，供其他模块通过端口查询 question 聚合数据，
 * 避免跨模块直接依赖 persistence 层 JPA 仓库。
 */
public interface QuestionQueryPort {

    List<Question> findByProjectSpaceId(Long projectSpaceId);

    List<Question> findByConversationId(Long conversationId);

    Optional<Question> findById(Long questionId);

    List<QueryPlan> findByQuestionIdIn(List<Long> questionIds);
}
