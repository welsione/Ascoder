package cn.welsione.ascoder.agent.persistence;

import cn.welsione.ascoder.agent.domain.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * LLM 供应商配置数据访问。
 */
public interface LlmProviderJpaRepository extends JpaRepository<LlmProvider, Long> {

    Optional<LlmProvider> findByName(String name);

    List<LlmProvider> findAllByOrderBySortOrderAsc();

    Optional<LlmProvider> findByIsDefaultTrueAndEnabledTrue();

    boolean existsByName(String name);
}
