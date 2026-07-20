package cn.welsione.ascoder.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 仓库分支引用 JPA 仓库接口。
 */
public interface RepositoryBranchJpaRepository extends JpaRepository<RepositoryBranch, Long> {

    List<RepositoryBranch> findByRepository_IdOrderByNameAscSourceKindAsc(Long repositoryId);

    List<RepositoryBranch> findByRepository_IdAndActiveTrueOrderByNameAscSourceKindAsc(Long repositoryId);

    Optional<RepositoryBranch> findByRepository_IdAndRefName(Long repositoryId, String refName);
}
