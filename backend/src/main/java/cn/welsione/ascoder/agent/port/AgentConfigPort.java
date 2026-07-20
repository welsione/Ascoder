package cn.welsione.ascoder.agent.port;

/**
 * AgentConfig 跨聚合只读查询端口，供 LlmProviderService 校验引用关系。
 */
public interface AgentConfigPort {

    /**
     * 统计引用指定 LLM 供应商的 Agent 数量。
     */
    long countByLlmProviderId(Long llmProviderId);
}
