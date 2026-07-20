package cn.welsione.ascoder.agent.extension.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 技能配置 JPA 仓库接口。
 */
public interface AgentSkillJpaRepository extends JpaRepository<AgentSkillConfig, Long> {

    Optional<AgentSkillConfig> findByName(String name);

    List<AgentSkillConfig> findByEnabledTrueOrderByCreatedAtDesc();

    List<AgentSkillConfig> findAllByOrderByCreatedAtDesc();
}
