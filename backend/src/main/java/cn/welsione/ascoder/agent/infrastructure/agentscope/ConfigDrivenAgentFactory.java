package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.port.ChatModelFactory;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 配置驱动的 Harness Agent 构建工厂，按 {@link AgentConfig} 构造运行时 Agent 实例。
 *
 * <p>替代原 {@code SpecialistAgentFactory} 的 {@code buildSpecialist} / {@code buildSynthesizer} 两个硬编码方法，
 * 统一为 {@link #build(AgentConfig, AgentRequest, AgentTooling)}。按 {@link AgentRole} 分支设置 role 专属的
 * HarnessAgent 参数（ORCHESTRATOR 有 workspace + compaction + toolResultEviction，SPECIALIST 无），
 * 这些 role 专属参数是代码级流程形状（D3），不进 AgentConfig 表。
 * AgentConfig 只驱动可变文本（sysPrompt）和数值参数（maxIters / 模型参数）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigDrivenAgentFactory {

    private final ChatModelFactory chatModelFactory;

    /**
     * 按 AgentConfig 构造 HarnessAgent。
     */
    public HarnessAgent build(AgentConfig config, AgentRequest request, AgentTooling tooling) {
        log.info("构建 HarnessAgent，agentId={}，role={}", config.getAgentId(), config.getAgentRole());
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(config.getAgentId())
                .description(config.getDescription())
                .sysPrompt(config.getSystemPrompt())
                .model(chatModelFactory.createModel(config))
                .toolkit(tooling.getToolkit())
                .maxIters(config.getMaxIters())
                .modelExecutionConfig(modelExecutionConfig(config))
                .toolExecutionConfig(toolExecutionConfig(config))
                .disableSubagents()
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableWorkspaceContext();
        if (config.getAgentRole() == AgentRole.ORCHESTRATOR) {
            builder.workspace(AgentRuntimeHelper.harnessWorkspace(request))
                    .compaction(CompactionConfig.builder()
                            .triggerMessages(30)
                            .keepMessages(10)
                            .flushBeforeCompact(true)
                            .offloadBeforeCompact(true)
                            .build())
                    .toolResultEviction(ToolResultEvictionConfig.defaults());
        } else if (config.getAgentRole() == AgentRole.SPECIALIST) {
            builder.workspace(AgentRuntimeHelper.specialistWorkspace(request));
        }
        return builder.build();
    }

    private ExecutionConfig modelExecutionConfig(AgentConfig config) {
        return ExecutionConfig.builder()
                .timeout(modelTimeout(config))
                .maxAttempts(chatModelFactory.modelMaxAttempts())
                .build();
    }

    private ExecutionConfig toolExecutionConfig(AgentConfig config) {
        return ExecutionConfig.builder()
                .timeout(chatModelFactory.toolTimeout())
                .maxAttempts(chatModelFactory.toolMaxAttempts())
                .build();
    }

    private Duration modelTimeout(AgentConfig config) {
        return config.getTimeoutSeconds() != null
                ? Duration.ofSeconds(config.getTimeoutSeconds())
                : chatModelFactory.timeout();
    }
}
