package cn.welsione.ascoder.agent.extension.mcp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * MCP 服务器配置 JPA 仓库接口。
 */
public interface McpServerJpaRepository extends JpaRepository<McpServerConfig, Long> {

    Optional<McpServerConfig> findByName(String name);

    List<McpServerConfig> findByEnabledTrueOrderByCreatedAtDesc();

    List<McpServerConfig> findAllByOrderByCreatedAtDesc();
}
