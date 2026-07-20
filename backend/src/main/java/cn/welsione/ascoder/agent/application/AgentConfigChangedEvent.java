package cn.welsione.ascoder.agent.application;

import lombok.Value;

/**
 * Agent 配置变更事件，用于通知缓存失效。
 *
 * <p>在 AgentConfigService 的 create/update/delete/updateEnabled 事务提交后发布，
 * 由 {@link AgentConfigCacheListener} 监听并清空 {@link AgentConfigCache}。</p>
 */
@Value
public class AgentConfigChangedEvent {
    String agentId;
    String action;
}
