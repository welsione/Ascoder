package cn.welsione.ascoder.repository;

import java.util.Optional;

/**
 * 代码仓库只读查询出口，供其他模块通过端口查询 repository 聚合数据，
 * 避免跨模块直接依赖 persistence 层 JPA 仓库。
 */
public interface RepositoryQueryPort {

    Optional<CodeRepository> findById(Long repositoryId);
}
