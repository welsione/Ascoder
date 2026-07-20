package cn.welsione.ascoder.repository.project;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 项目仓库成员的 JPA 仓库。 */
public interface ProjectRepositoryJpaRepository extends JpaRepository<ProjectRepository, Long> {

    @EntityGraph(attributePaths = {"project", "repository"})
    List<ProjectRepository> findByProject_IdOrderBySortOrderAscCreatedAtAsc(Long projectId);

    boolean existsByProject_IdAndRepository_Id(Long projectId, Long repositoryId);

    boolean existsByProject_IdAndAlias(Long projectId, String alias);
}
