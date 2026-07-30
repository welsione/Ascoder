package cn.welsione.ascoder.repository.projectspace;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 项目空间实体的 JPA 仓库。 */
public interface ProjectSpaceJpaRepository extends JpaRepository<ProjectSpace, Long> {

    @Override
    @EntityGraph(attributePaths = "project")
    Optional<ProjectSpace> findById(Long id);

    @EntityGraph(attributePaths = "project")
    List<ProjectSpace> findAllByOrderByCreatedAtDesc();

    boolean existsByProject_IdAndName(Long projectId, String name);

    List<ProjectSpace> findByProject_Id(Long projectId);
}
