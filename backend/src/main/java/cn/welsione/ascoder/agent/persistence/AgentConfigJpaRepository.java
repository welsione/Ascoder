package cn.welsione.ascoder.agent.persistence;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * AgentConfig JPA 仓库，提供按 agentId、启用状态、角色的查询。
 *
 * <p>采用直接注入 JpaRepository 模式，与现有 AgentSkillJpaRepository / McpServerJpaRepository 风格一致。</p>
 */
public interface AgentConfigJpaRepository extends JpaRepository<AgentConfig, Long> {

    Optional<AgentConfig> findByAgentId(String agentId);

    List<AgentConfig> findByEnabledTrueOrderBySortOrderAsc();

    List<AgentConfig> findByAgentRoleAndEnabledTrue(AgentRole agentRole);

    boolean existsByAgentId(String agentId);

    long countByAgentRoleAndEnabledTrue(AgentRole agentRole);

    long countByLlmProviderId(Long llmProviderId);
}
