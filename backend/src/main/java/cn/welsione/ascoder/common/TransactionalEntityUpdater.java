package cn.welsione.ascoder.common;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

/**
 * 事务内实体状态回写工具。
 *
 * <p>在短事务内按 id 重新加载受管实体并应用变更，避免游离实体经 merge 覆盖并发修改。
 * 适用于长耗时操作（git、CodeGraph、LLM）在事务外执行后，将结果回写 DB 的场景：
 * 先在事务外完成耗时计算，再通过本工具以短事务回写状态。</p>
 */
public final class TransactionalEntityUpdater {

    private TransactionalEntityUpdater() {
    }

    /**
     * 在短事务内按 id 重新加载受管实体并应用变更。
     *
     * @param transactionTemplate 事务模板，由调用方注入
     * @param repository          实体仓库
     * @param id                  实体 id
     * @param updater             对受管实体应用的变更
     * @param entityName          实体中文名，用于不存在时的异常信息
     * @param <T>                 实体类型
     * @return 回写后的受管实体
     * @throws ResourceNotFoundException 实体不存在
     */
    public static <T> T updateById(
            TransactionTemplate transactionTemplate,
            CrudRepository<T, Long> repository,
            Long id,
            Consumer<T> updater,
            String entityName
    ) {
        return transactionTemplate.execute(status -> {
            T managed = repository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(entityName, id));
            updater.accept(managed);
            return managed;
        });
    }
}
