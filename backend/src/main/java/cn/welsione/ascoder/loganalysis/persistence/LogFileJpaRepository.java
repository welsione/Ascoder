package cn.welsione.ascoder.loganalysis.persistence;

import cn.welsione.ascoder.loganalysis.domain.LogFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 日志文件的 JPA 仓库。 */
public interface LogFileJpaRepository extends JpaRepository<LogFile, Long> {

    List<LogFile> findByUpload_IdOrderByCreatedAtAsc(Long uploadId);
}
