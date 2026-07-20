package cn.welsione.ascoder.selflearning;

/**
 * 自学习查询端口，向 agent 模块暴露知识/洞察/原始记录只读检索能力，
 * 避免 agent 模块直接依赖 selflearning 的持久化层。
 */
public interface SelfLearningQueryPort {

    /**
     * 创建自学习检索工具集，供 researcher 子 agent 在运行时查询知识与候选洞察。
     */
    SelfLearningAgentTools createAgentTools(Long projectSpaceId);
}
