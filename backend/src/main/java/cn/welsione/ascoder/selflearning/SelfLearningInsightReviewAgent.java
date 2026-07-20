package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.repository.projectspace.ProjectSpace;

import java.util.List;

/**
 * 候选洞察审核 Agent 端口，负责基于当前项目代码复核洞察并协助管理员微调。
 */
public interface SelfLearningInsightReviewAgent {

    SelfLearningInsightVerification verify(ProjectSpace projectSpace, LearningInsight insight, List<LearningRawEvent> rawEvents);

    SelfLearningInsightDraft refine(
            ProjectSpace projectSpace,
            LearningInsight insight,
            List<LearningRawEvent> rawEvents,
            String instruction
    );
}
