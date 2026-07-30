package cn.welsione.ascoder.common.transaction;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

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
 * <p><b>事务语义</b>：实现类使用 {@code PROPAGATION_REQUIRES_NEW}，
 * 每次回写开启独立事务，回调正常返回即提交，不受外层事务回滚影响。
 * 这降低了调用方的心智负担--即使回写后抛出业务异常，已提交的状态也不会丢失。</p>
 */
public interface EntityUpdater {

    /**
     * 在独立短事务内按 id 重新加载受管实体并应用变更。
     *
     * @param finder    按 id 加载实体的函数（通常为 repository::findById）
     * @param saver     保存实体的函数（通常为 repository::save）
     * @param id        实体 id
     * @param updater   对受管实体应用的变更
     * @param entityName 实体中文名，用于不存在时的异常信息
     * @param <T>       实体类型
     * @return 回写后的受管实体
     * @throws cn.welsione.ascoder.common.exception.ResourceNotFoundException 实体不存在
     */
    <T> T updateById(Function<Long, Optional<T>> finder,
                     Function<T, T> saver,
                     Long id,
                     Consumer<T> updater,
                     String entityName);

    /**
     * 在独立短事务内查找或创建实体，并应用变更后保存。
     *
     * <p>用于 find-or-create 场景：先按 finder 查找，不存在则用 creator 创建，
     * 然后应用 updater 变更并保存。整个流程在短事务内原子完成，消除 find 与 save
     * 之间的竞态窗口。</p>
     *
     * @param finder  查找现有实体的函数（返回 Optional）
     * @param creator 实体不存在时创建新实体的函数
     * @param saver   保存实体的函数（通常为 repository::saveAndFlush）
     * @param updater 对实体应用的变更（如状态流转）
     * @param <T>     实体类型
     * @return 保存后的实体
     */
    <T> T findOrSave(java.util.function.Supplier<Optional<T>> finder,
                     java.util.function.Supplier<T> creator,
                     Function<T, T> saver,
                     Consumer<T> updater);
}
