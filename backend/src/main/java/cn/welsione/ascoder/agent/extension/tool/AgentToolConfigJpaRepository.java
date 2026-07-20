package cn.welsione.ascoder.agent.extension.tool;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 工具配置仓储，负责按工具 key 读取启停配置。
 */
public interface AgentToolConfigJpaRepository extends JpaRepository<AgentToolConfig, Long> {

    Optional<AgentToolConfig> findByToolKey(String toolKey);

    List<AgentToolConfig> findByToolKeyIn(List<String> toolKeys);
}
