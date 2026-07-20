package cn.welsione.ascoder.loganalysis.persistence;

import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 日志上传记录的 JPA 仓库。 */
public interface LogUploadJpaRepository extends JpaRepository<LogUpload, Long> {

    List<LogUpload> findTop20ByProjectSpace_IdOrderByCreatedAtDesc(Long projectSpaceId);
}
