package cn.welsione.ascoder.repository.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 项目实体的 JPA 仓库。 */
public interface ProjectJpaRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrderByCreatedAtDesc();

    boolean existsByName(String name);
}
