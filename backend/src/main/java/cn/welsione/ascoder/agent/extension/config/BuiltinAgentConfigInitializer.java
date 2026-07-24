package cn.welsione.ascoder.agent.extension.config;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.persistence.AgentConfigJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 应用启动时将内置 Agent 配置写入数据库，缺失则新增、已存在则跳过。
 *
 * <p>与 {@link cn.welsione.ascoder.agent.extension.skill.BuiltinSkillInitializer} 对齐，
 * 作为 Flyway V31/V33 的运行时兜底：当数据库中 agentId 缺失时（如迁移历史与 schema
 * 不同步、记录被误删）补全内置 Agent，避免问答流程因找不到启用的 ORCHESTRATOR 而失败。</p>
 *
 * <p>已存在的记录不会被覆盖，管理员通过界面或后续 Flyway 迁移的 prompt 修改得以保留。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinAgentConfigInitializer implements ApplicationRunner {

    private final AgentConfigJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (BuiltinAgentConfigDefinition definition : BuiltinAgentConfigCatalog.all()) {
            if (repository.findByAgentId(definition.getAgentId()).isPresent()) {
                continue;
            }
            repository.save(toEntity(definition));
            created++;
        }
        if (created > 0) {
            log.warn("内置 Agent 配置初始化完成，补全数量={}（数据库缺失，已由 Initializer 兜底创建）", created);
        }
    }

    private AgentConfig toEntity(BuiltinAgentConfigDefinition definition) {
        AgentConfig config = new AgentConfig();
        config.setAgentId(definition.getAgentId());
        config.setDisplayName(definition.getDisplayName());
        config.setDescription(definition.getDescription());
        config.setAgentRole(definition.getAgentRole());
        config.setTaskKind(definition.getTaskKind());
        config.setSystemPrompt(definition.getSystemPrompt());
        config.setTaskTemplate(definition.getTaskTemplate());
        config.setMaxIters(definition.getMaxIters());
        config.setMaxTokens(definition.getMaxTokens());
        config.setRoleKeysJson(toJson(definition.getRoleKeys()));
        config.setQuestionKeywordsJson(toJson(definition.getQuestionKeywords()));
        config.setToolGroupKeysJson(toJson(definition.getToolGroupKeys()));
        config.setSkillNamesJson(toJson(definition.getSkillNames()));
        config.setMcpServerNamesJson(toJson(definition.getMcpServerNames()));
        config.setRequired(definition.isRequired());
        config.setEnabled(definition.isEnabled());
        config.setBuiltin(true);
        config.setHandoffTitle(definition.getHandoffTitle());
        config.setHandoffDescription(definition.getHandoffDescription());
        config.setReturnTitle(definition.getReturnTitle());
        config.setReturnDescription(definition.getReturnDescription());
        config.setSortOrder(definition.getSortOrder());
        return config;
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
