package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.repository.projectspace.ProjectSpace;

import java.util.List;
import java.util.Optional;

/**
 * Self Learning Agent 端口，负责把完整会话原始记录整理成可审核的候选洞察草稿。
 */
public interface SelfLearningInsightAgent {

    Optional<SelfLearningInsightDraft> summarize(ProjectSpace projectSpace, List<LearningRawEvent> rawEvents);
}
