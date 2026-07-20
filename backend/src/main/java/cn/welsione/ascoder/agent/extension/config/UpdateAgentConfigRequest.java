package cn.welsione.ascoder.agent.extension.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 更新 Agent 配置的请求体，字段集与 {@link CreateAgentConfigRequest} 相同。
 *
 * <p>继承而非复制字段以遵循禁止重复代码原则。builtin Agent 的 agentId / agentRole 不可改，
 * 由 Service 层校验。</p>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UpdateAgentConfigRequest extends CreateAgentConfigRequest {
}
