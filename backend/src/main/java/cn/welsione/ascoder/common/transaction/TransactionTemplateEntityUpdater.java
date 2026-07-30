package cn.welsione.ascoder.common.transaction;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@link EntityUpdater} 的默认实现，基于 {@link TransactionTemplate} 短事务回写。
 *
 * <p>使用 {@code PROPAGATION_REQUIRES_NEW} 每次开启独立事务，不受外层事务回滚影响，
 * 确保状态落盘。即使调用方在回写后抛出异常，已提交的状态也不会丢失。</p>
 */
@Component
@RequiredArgsConstructor
public class TransactionTemplateEntityUpdater implements EntityUpdater {

    private final PlatformTransactionManager transactionManager;

    @Override
    public <T> T updateById(Function<Long, Optional<T>> finder,
                            Function<T, T> saver,
                            Long id,
                            Consumer<T> updater,
                            String entityName) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return requiresNew.execute(status -> {
            T managed = finder.apply(id)
                    .orElseThrow(() -> new ResourceNotFoundException(entityName, id));
            updater.accept(managed);
            // 显式 save 确保状态立即写入，不依赖 JPA 隐式脏检查
            return saver.apply(managed);
        });
    }

    @Override
    public <T> T save(Function<T, T> saver, T entity) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return requiresNew.execute(status -> saver.apply(entity));
    }
}
