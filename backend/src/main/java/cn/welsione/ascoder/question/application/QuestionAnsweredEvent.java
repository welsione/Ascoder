package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.question.api.QuestionResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 问题回答完成事件，在问题事务提交后发布，
 * 供自学习模块监听以异步沉淀候选洞察，解耦 question 与 selflearning 模块。
 */
@Getter
@AllArgsConstructor
public class QuestionAnsweredEvent {

    private final Long questionId;
    private final QuestionResponse response;
    private final String fullAnswer;
}
