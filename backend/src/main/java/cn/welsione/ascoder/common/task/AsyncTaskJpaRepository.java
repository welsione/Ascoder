package cn.welsione.ascoder.common.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 异步任务持久化仓库。
 */
public interface AsyncTaskJpaRepository extends JpaRepository<AsyncTask, Long> {

    /** 查找指定类型和业务 ID 的活跃任务（QUEUED 或 RUNNING）。 */
    List<AsyncTask> findByKindAndBusinessIdAndStatusIn(TaskKind kind, Long businessId, List<TaskStatus> statuses);

    /** 查找指定类型的活跃任务。 */
    List<AsyncTask> findByKindAndStatusIn(TaskKind kind, List<TaskStatus> statuses);

    /** 查找所有未完成的任务（用于启动恢复）。 */
    List<AsyncTask> findByStatusIn(List<TaskStatus> statuses);

    /** 查找指定业务 ID 的最新任务。 */
    Optional<AsyncTask> findTopByBusinessIdOrderByQueuedAtDesc(Long businessId);
}
