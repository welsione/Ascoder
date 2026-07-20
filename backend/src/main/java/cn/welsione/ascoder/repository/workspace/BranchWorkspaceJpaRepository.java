package cn.welsione.ascoder.repository.workspace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 分支工作区实体的 JPA 仓库。 */
public interface BranchWorkspaceJpaRepository extends JpaRepository<BranchWorkspace, Long> {

    @Override
    @EntityGraph(attributePaths = "repository")
    Optional<BranchWorkspace> findById(Long id);

    @EntityGraph(attributePaths = "repository")
    List<BranchWorkspace> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "repository")
    List<BranchWorkspace> findByRepository_IdOrderByBranchNameAsc(Long repositoryId);

    Optional<BranchWorkspace> findByRepository_IdAndBranchName(Long repositoryId, String branchName);
}
