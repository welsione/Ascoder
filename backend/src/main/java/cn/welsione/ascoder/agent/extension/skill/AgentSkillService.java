package cn.welsione.ascoder.agent.extension.skill;

import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent 技能服务，处理技能的 CRUD 操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSkillService {

    private final AgentSkillJpaRepository repository;

    @Transactional(readOnly = true)
    public List<AgentSkillConfig> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public AgentSkillConfig create(CreateAgentSkillRequest request) {
        log.info("创建技能，name={}", request.getName());
        repository.findByName(request.getName().trim()).ifPresent(existing -> {
            throw new DuplicateException("Skill 名称已存在");
        });

        AgentSkillConfig skill = AgentSkillMapper.INSTANCE.toEntity(request);
        return repository.save(skill);
    }

    @Transactional
    public AgentSkillConfig updateEnabled(Long id, UpdateAgentSkillEnabledRequest request) {
        AgentSkillConfig skill = getEntity(id);
        skill.setEnabled(request.isEnabled());
        log.info("更新技能启用状态，id={}，enabled={}", id, request.isEnabled());
        return repository.save(skill);
    }

    @Transactional(readOnly = true)
    public AgentSkillConfig getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill", id));
    }
}
