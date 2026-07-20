package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Self Learning Agent 的规则兜底实现，仅在没有 LLM Agent Bean 时提供保底草稿。
 */
@Component
@ConditionalOnMissingBean(SelfLearningInsightAgent.class)
public class RuleBasedSelfLearningInsightAgent implements SelfLearningInsightAgent {

    @Override
    public Optional<SelfLearningInsightDraft> summarize(ProjectSpace projectSpace, List<LearningRawEvent> rawEvents) {
        return Optional.empty();
    }
}
