package cn.welsione.ascoder.agent.port;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import io.agentscope.core.model.AnthropicChatModel;

import java.time.Duration;

/**
 * ChatModel 工厂端口接口，按供应商配置创建 ChatModel 实例。
 *
 * <p>Agent 类依赖此接口而非具体实现，符合依赖倒置原则。
 * 实现类从数据库读取供应商配置，支持多供应商动态切换。</p>
 */
public interface ChatModelFactory {

    /**
     * 按 AgentConfig 创建 ChatModel，含供应商选择和参数覆盖逻辑。
     */
    AnthropicChatModel createModel(AgentConfig config);

    /**
     * 使用默认供应商创建 ChatModel，供不按 AgentConfig 选择供应商的场景。
     */
    AnthropicChatModel createDefaultModel();

    /**
     * 解析 AgentConfig 对应的供应商配置快照。
     */
    ResolvedModelConfig resolveProvider(AgentConfig config);

    /**
     * 模型调用超时。
     */
    Duration timeout();

    /**
     * 工具调用超时。
     */
    Duration toolTimeout();

    /**
     * 默认最大迭代次数。
     */
    int maxIters();

    /**
     * 模型最大重试次数。
     */
    int modelMaxAttempts();

    /**
     * 工具最大重试次数。
     */
    int toolMaxAttempts();
}
