package cn.welsione.ascoder.common.transaction;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

/**
 * {@link EntityUpdater} 的默认实现，基于 {@link TransactionTemplate} 短事务回写。
 *
 * <p>使用 {@code PROPAGATION_REQUIRED}，在无外层事务时开启独立事务，
 * 回调正常返回即提交，确保状态落盘。</p>
 */
@Component
@RequiredArgsConstructor
public class TransactionTemplateEntityUpdater implements EntityUpdater {

    private final TransactionTemplate transactionTemplate;

    @Override
    public <T> T updateById(EntityRepository<T> repository,
                            Long id,
                            Consumer<T> updater,
                            String entityName) {
        return transactionTemplate.execute(status -> {
            T managed = repository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(entityName, id));
            updater.accept(managed);
            // 显式 save 确保状态立即写入，不依赖 JPA 隐式脏检查
            return repository.save(managed);
        });
    }
}
