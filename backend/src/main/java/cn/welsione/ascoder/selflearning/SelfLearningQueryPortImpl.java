package cn.welsione.ascoder.selflearning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SelfLearningQueryPort} 实现，注入 selflearning 持久化层构造 Agent 工具集。
 */
@Component
@RequiredArgsConstructor
public class SelfLearningQueryPortImpl implements SelfLearningQueryPort {

    private final LearningKnowledgeItemJpaRepository knowledgeRepository;
    private final LearningInsightJpaRepository insightRepository;
    private final LearningRawEventJpaRepository rawEventRepository;

    @Override
    public SelfLearningAgentTools createAgentTools(Long projectSpaceId) {
        return new SelfLearningAgentTools(projectSpaceId, knowledgeRepository, insightRepository, rawEventRepository);
    }
}
