package cn.welsione.ascoder.selflearning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 纠错记录仓库，保存项目空间内的错误修正候选和已验证纠错。
 */
public interface LearningCorrectionJpaRepository extends JpaRepository<LearningCorrection, Long> {
    List<LearningCorrection> findByProjectSpace_IdOrderByUpdatedAtDesc(Long projectSpaceId);

    Optional<LearningCorrection> findByIdAndProjectSpace_Id(Long id, Long projectSpaceId);
}
