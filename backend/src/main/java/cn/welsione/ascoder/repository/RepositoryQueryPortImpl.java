package cn.welsione.ascoder.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link RepositoryQueryPort} 的默认实现，封装代码仓库的只读查询，
 * 收口 persistence 层依赖，对外仅暴露端口方法。
 */
@Component
@RequiredArgsConstructor
public class RepositoryQueryPortImpl implements RepositoryQueryPort {

    private final CodeRepositoryJpaRepository codeRepositoryRepository;

    @Override
    public Optional<CodeRepository> findById(Long repositoryId) {
        if (repositoryId == null) {
            return Optional.empty();
        }
        return codeRepositoryRepository.findById(repositoryId);
    }
}
