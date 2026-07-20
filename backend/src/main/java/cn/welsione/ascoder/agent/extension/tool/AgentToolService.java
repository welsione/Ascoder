package cn.welsione.ascoder.agent.extension.tool;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 工具管理服务，提供工具目录同步、启停配置和运行时过滤能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolService {

    private final AgentToolConfigJpaRepository repository;

    @Transactional
    public List<AgentToolConfig> list() {
        return syncCatalog();
    }

    @Transactional
    public AgentToolConfig updateEnabled(Long id, UpdateAgentToolEnabledRequest request) {
        AgentToolConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AgentToolConfig", id));
        config.updateEnabled(request.isEnabled());
        log.info("更新 Agent 工具启停状态，toolKey={}，enabled={}", config.getToolKey(), config.isEnabled());
        return repository.save(config);
    }

    @Transactional(readOnly = true)
    public Set<String> enabledToolKeys() {
        List<AgentToolDefinition> definitions = AgentToolCatalog.all();
        List<String> toolKeys = definitions.stream()
                .map(AgentToolDefinition::getToolKey)
                .toList();
        Map<String, AgentToolConfig> configs = repository.findByToolKeyIn(toolKeys).stream()
                .collect(Collectors.toMap(AgentToolConfig::getToolKey, Function.identity()));
        return definitions.stream()
                .filter(definition -> enabled(definition, configs.get(definition.getToolKey())))
                .map(AgentToolDefinition::getToolKey)
                .collect(Collectors.toSet());
    }

    private List<AgentToolConfig> syncCatalog() {
        List<AgentToolDefinition> definitions = AgentToolCatalog.all();
        Map<String, AgentToolConfig> existing = repository.findByToolKeyIn(
                        definitions.stream().map(AgentToolDefinition::getToolKey).toList()
                ).stream()
                .collect(Collectors.toMap(AgentToolConfig::getToolKey, Function.identity()));

        List<AgentToolConfig> synced = definitions.stream()
                .map(definition -> syncDefinition(definition, existing.get(definition.getToolKey())))
                .toList();
        return repository.saveAll(synced);
    }

    private AgentToolConfig syncDefinition(AgentToolDefinition definition, AgentToolConfig existing) {
        if (existing == null) {
            AgentToolConfig config = new AgentToolConfig();
            config.setToolKey(definition.getToolKey());
            config.setDisplayName(definition.getDisplayName());
            config.setGroupName(definition.getGroupName());
            config.setRiskLevel(definition.getRiskLevel());
            config.setDescription(definition.getDescription());
            config.setEnabled(definition.isDefaultEnabled());
            config.setBuiltin(true);
            return config;
        }
        existing.updateFromDefinition(definition);
        return existing;
    }

    private boolean enabled(AgentToolDefinition definition, AgentToolConfig config) {
        if (config == null) {
            return definition.isDefaultEnabled();
        }
        return config.isEnabled();
    }
}
