package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.selflearning.SelfLearningAgentTools;
import cn.welsione.ascoder.selflearning.SelfLearningQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 自学习 Agent 工具构建工厂，通过 SelfLearningQueryPort 创建工具集，
 * 使 agent 模块不直接依赖 selflearning 持久化细节。
 */
@Component
@RequiredArgsConstructor
class SelfLearningAgentToolFactory {

    private final SelfLearningQueryPort selfLearningQueryPort;

    /**
     * 创建自学习检索工具集，供 researcher 子 agent 查询知识与候选洞察。
     */
    SelfLearningAgentTools createTools(Long projectSpaceId) {
        return selfLearningQueryPort.createAgentTools(projectSpaceId);
    }
}
