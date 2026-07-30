package cn.welsione.ascoder.common.transaction;

import java.util.Optional;

/**
 * 实体仓库业务端口，仅暴露回写所需的只读查询能力。
 *
 * <p>作为 {@link EntityUpdater} 的依赖，避免业务 port 层直接依赖 Spring Data 的
 * {@code CrudRepository} 基础设施接口。各聚合的 JPA 仓库实现本端口适配即可。</p>
 *
 * @param <T> 实体类型
 */
public interface EntityRepository<T> {

    /**
     * 按 id 查询实体。
     *
     * @param id 实体 id
     * @return 实体，不存在时为空
     */
    Optional<T> findById(Long id);
}
