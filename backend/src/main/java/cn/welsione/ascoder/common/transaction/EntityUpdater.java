package cn.welsione.ascoder.common.transaction;

import java.util.function.Consumer;

/**
 * 事务内实体状态回写端口。
 *
 * <p>在短事务内按 id 重新加载受管实体并应用变更，避免游离实体经 merge 覆盖并发修改。
 * 适用于长耗时操作（git、CodeGraph、LLM）在事务外执行后，将结果回写 DB 的场景：
 * 先在事务外完成耗时计算，再通过本端口以短事务回写状态。</p>
 *
 * <p>业务模块通过注入本端口调用回写能力，不直接依赖事务基础设施实现，
 * 符合项目 port/infrastructure 分层规范。</p>
 *
 * <p><b>事务语义</b>：实现类使用 {@code PROPAGATION_REQUIRED}，
 * 在无外层事务时开启独立事务，回调正常返回即提交。调用方须确保：</p>
 * <ul>
 *   <li>回写方法本身无 {@code @Transactional}，使回写开启独立事务</li>
 *   <li>若回写后需抛出业务异常（如"先回写失败状态、再抛异常"模式），须确保回写事务已提交，
 *       否则外层事务回滚会丢失状态变更</li>
 * </ul>
 */
public interface EntityUpdater {

    /**
     * 在短事务内按 id 重新加载受管实体并应用变更。
     *
     * @param repository 实体仓库端口
     * @param id         实体 id
     * @param updater    对受管实体应用的变更
     * @param entityName 实体中文名，用于不存在时的异常信息
     * @param <T>        实体类型
     * @return 回写后的受管实体
     * @throws cn.welsione.ascoder.common.exception.ResourceNotFoundException 实体不存在
     */
    <T> T updateById(EntityRepository<T> repository,
                     Long id,
                     Consumer<T> updater,
                     String entityName);
}
