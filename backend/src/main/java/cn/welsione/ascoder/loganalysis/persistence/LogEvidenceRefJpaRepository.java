package cn.welsione.ascoder.loganalysis.persistence;

import cn.welsione.ascoder.loganalysis.domain.LogEvidenceRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 日志分析证据引用的 JPA 仓库。 */
public interface LogEvidenceRefJpaRepository extends JpaRepository<LogEvidenceRef, Long> {

    List<LogEvidenceRef> findByTask_Id(Long taskId);
}
