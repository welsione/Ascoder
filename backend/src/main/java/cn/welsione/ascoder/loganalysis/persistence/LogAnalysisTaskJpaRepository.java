package cn.welsione.ascoder.loganalysis.persistence;

import cn.welsione.ascoder.loganalysis.domain.LogAnalysisTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 日志分析任务的 JPA 仓库。 */
public interface LogAnalysisTaskJpaRepository extends JpaRepository<LogAnalysisTask, Long> {

    Optional<LogAnalysisTask> findByQuestion_Id(Long questionId);
}
