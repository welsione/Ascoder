package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 术语仓库，管理项目空间内的术语和业务语境。
 */
public interface LearningTermJpaRepository extends JpaRepository<LearningTerm, Long> {
    List<LearningTerm> findByProjectSpace_IdOrderByUpdatedAtDesc(Long projectSpaceId);

    Optional<LearningTerm> findByIdAndProjectSpace_Id(Long id, Long projectSpaceId);

    boolean existsByProjectSpace_IdAndTerm(Long projectSpaceId, String term);
}
