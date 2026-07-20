package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.question.application.QuestionAnsweredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 自学习事件监听器，监听问题回答完成事件并触发候选洞察沉淀。
 * <p>
 * 独立成 bean 以便跨 bean 调用 {@link InsightService#createCandidateFromAnswer}，
 * 保证其 {@code @Transactional} 声明在事件回调上下文中正常生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelfLearningEventListener {

    private final InsightService insightService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuestionAnswered(QuestionAnsweredEvent event) {
        try {
            insightService.createCandidateFromAnswer(event.getQuestionId(), event.getResponse(), event.getFullAnswer());
        } catch (Exception ex) {
            log.warn("事件驱动沉淀自学习候选经验失败，questionId={}，错误={}",
                    event.getQuestionId(), ex.getMessage());
        }
    }
}
