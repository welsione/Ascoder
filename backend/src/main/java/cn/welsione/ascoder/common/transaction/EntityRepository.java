package cn.welsione.ascoder.common.transaction;

import java.util.Optional;

/**
 * 实体仓库业务端口，仅暴露回写所需的只读查询能力。
 *
 * <p>作为 {@link EntityUpdater} 的依赖，避免业务 port 层直接依赖 Spring Data 的
 * {@code CrudRepository} 基础设施接口。各聚合的 JPA 仓库实现本端口适配即可。</p>
 *
 * <p><b>与 Service 的关系</b>：Service 在聚合内可持有 JPA 仓库（项目分层惯例：
 * Controller -> Service -> Repository），但传递给 {@link EntityUpdater#updateById}
 * 时以本端口类型出现，确保 EntityUpdater 仅依赖 {@code findById} 业务能力，
 * 不触及 JpaRepository 的基础设施方法。</p>
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

    /**
     * 保存实体状态，立即 flush 确保落盘。
     *
     * @param entity 受管实体
     * @return 保存后的实体
     */
    T save(T entity);
}
