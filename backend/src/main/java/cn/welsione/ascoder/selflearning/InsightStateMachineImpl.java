package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.common.exception.InvalidStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * 候选洞察状态机实现，用静态转换表守卫所有状态变更。
 */
@Slf4j
@Component
public class InsightStateMachineImpl implements InsightStateMachine {

    private static final Map<LearningInsightStatus, Set<LearningInsightStatus>> TRANSITIONS = Map.of(
            LearningInsightStatus.PENDING_REVIEW, Set.of(LearningInsightStatus.APPROVED, LearningInsightStatus.REJECTED),
            LearningInsightStatus.APPROVED, Set.of(LearningInsightStatus.MERGED)
    );

    @Override
    public void transition(LearningInsight insight, LearningInsightStatus target, String reviewerComment) {
        LearningInsightStatus current = insight.getStatus();
        Set<LearningInsightStatus> allowed = TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            throw new InvalidStateException(
                    "洞察状态不允许从 " + current + " 转换到 " + target
            );
        }
        insight.setStatus(target);
        insight.setReviewComment(reviewerComment);
        insight.setReviewedAt(new Date());
        insight.touch();
        log.debug("洞察状态转换：insightId={}，{} → {}", insight.getId(), current, target);
    }
}
