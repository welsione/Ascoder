package cn.welsione.ascoder.agent.extension.config;

import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.domain.SpecialistTaskKind;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * 内置 Agent 配置的不可变定义，描述单个预置 Agent 的全部字段。
 *
 * <p>作为 {@link BuiltinAgentConfigInitializer} 的数据源，仅在数据库缺失对应 agentId 时
 * 用于补全，不覆盖已存在的用户改动。prompt 内容与 Flyway V31/V33 迁移脚本保持一致，
 * 保证单一数据源原则下的运行时兜底。</p>
 */
@Value
@AllArgsConstructor
public class BuiltinAgentConfigDefinition {

    String agentId;
    String displayName;
    String description;
    AgentRole agentRole;
    /** SPECIALIST 必填，ORCHESTRATOR / SELF_LEARNING 为 null。 */
    SpecialistTaskKind taskKind;
    String systemPrompt;
    String taskTemplate;
    int maxIters;
    Integer maxTokens;
    List<String> roleKeys;
    List<String> questionKeywords;
    List<String> toolGroupKeys;
    List<String> skillNames;
    List<String> mcpServerNames;
    boolean required;
    boolean enabled;
    String handoffTitle;
    String handoffDescription;
    String returnTitle;
    String returnDescription;
    int sortOrder;
}
