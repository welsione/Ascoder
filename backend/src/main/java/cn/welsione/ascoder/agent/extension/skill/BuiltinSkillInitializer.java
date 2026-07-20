package cn.welsione.ascoder.agent.extension.skill;

import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 应用启动时将内置 Skill 写入数据库，缺失则新增、已存在则跳过。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinSkillInitializer implements ApplicationRunner {

    private final AgentSkillJpaRepository skillRepository;
    private final PromptManager promptManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (BuiltinSkillDefinition definition : BuiltinSkillCatalog.all(promptManager)) {
            if (skillRepository.findByName(definition.getName()).isPresent()) {
                continue;
            }
            AgentSkillConfig skill = new AgentSkillConfig();
            skill.setName(definition.getName());
            skill.setDescription(definition.getDescription());
            skill.setSkillContent(definition.getSkillContent());
            skill.setSource("builtin");
            skill.setEnabled(true);
            skillRepository.save(skill);
            created++;
        }
        if (created > 0) {
            log.info("内置 Skill 初始化完成，新增数量={}", created);
        }
    }
}
