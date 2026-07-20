package cn.welsione.ascoder.repository.projectspace;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 项目空间成员的 JPA 仓库。 */
public interface ProjectSpaceMemberJpaRepository extends JpaRepository<ProjectSpaceMember, Long> {

    @EntityGraph(attributePaths = {"projectSpace", "projectSpace.project", "repository", "branchWorkspace"})
    List<ProjectSpaceMember> findByProjectSpace_IdOrderByCreatedAtAsc(Long projectSpaceId);
}
