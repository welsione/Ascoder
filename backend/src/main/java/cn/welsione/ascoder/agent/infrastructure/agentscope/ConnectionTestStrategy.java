package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import cn.welsione.ascoder.agent.domain.ConnectionTestResult;

/**
 * 供应商连接测试策略接口，按协议类型执行不同的连接验证。
 */
public interface ConnectionTestStrategy {

    /**
     * 判断此策略是否支持指定的供应商协议类型。
     */
    boolean supports(String providerType);

    /**
     * 执行连接测试。
     */
    ConnectionTestResult test(ResolvedModelConfig config);
}
