package cn.welsione.ascoder.codegraph.infrastructure.cli;

import cn.welsione.ascoder.common.task.AsyncTask;
import cn.welsione.ascoder.common.task.AsyncTaskJpaRepository;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CodeGraph 索引进度跟踪器。
 *
 * <p>优先从 asyncTasks 表读取进度（支持持久化和重启恢复），
 * 内存进度作为后备（兼容旧调用路径）。</p>
 */
@Slf4j
public class IndexProgressTracker {

    private final AsyncTaskJpaRepository taskRepository;
    private final Map<Long, IndexProgress> progressMap = new ConcurrentHashMap<>();

    public IndexProgressTracker(AsyncTaskJpaRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 开始跟踪指定项目空间的索引进度。
     */
    public void start(Long projectSpaceId) {
        progressMap.put(projectSpaceId, new IndexProgress(0, "开始索引", false));
        log.info("开始跟踪项目空间 {} 的索引进度", projectSpaceId);
    }

    /**
     * 更新指定项目空间的索引进度。
     *
     * <p>当 percent < 0 时保留上一次的百分比，只更新消息文本。
     * 这确保了无法提取百分比的输出行不会导致进度倒退。</p>
     */
    public void update(Long projectSpaceId, int percent, String message) {
        progressMap.compute(projectSpaceId, (id, existing) -> {
            int effectivePercent = percent >= 0 ? percent : (existing != null ? existing.getPercent() : 0);
            return new IndexProgress(effectivePercent, message, existing != null && existing.isCompleted());
        });
        log.debug("项目空间 {} 索引进度: {}% - {}", projectSpaceId,
                percent >= 0 ? percent : "保留", message);
    }

    /**
     * 标记指定项目空间的索引完成。
     */
    public void complete(Long projectSpaceId) {
        progressMap.put(projectSpaceId, new IndexProgress(100, "索引完成", true));
        log.info("项目空间 {} 索引完成", projectSpaceId);
    }

    /**
     * 标记指定项目空间的索引失败。
     */
    public void fail(Long projectSpaceId, String error) {
        progressMap.put(projectSpaceId, new IndexProgress(0, error, true));
        log.error("项目空间 {} 索引失败: {}", projectSpaceId, error);
    }

    /**
     * 获取指定项目空间的当前进度。
     *
     * <p>优先返回内存进度（CLI 实时写入），DB 查询作为重启恢复的后备。
     * 若优先查 DB，会形成"读旧值→写旧值"的死循环：
     * CLI 写内存 → 同步线程从 DB 读旧值 → 写回旧值到 DB → 进度永远不更新。</p>
     */
    public IndexProgress get(Long projectSpaceId) {
        // 优先返回内存进度（CLI 实时写入的）
        IndexProgress memoryProgress = progressMap.get(projectSpaceId);
        if (memoryProgress != null) {
            return memoryProgress;
        }

        // 内存无记录（重启恢复场景），从 DB 读取
        try {
            List<AsyncTask> active = taskRepository.findByKindAndBusinessIdAndStatusIn(
                    TaskKind.CODEGRAPH_INDEX, projectSpaceId,
                    List.of(TaskStatus.QUEUED, TaskStatus.RUNNING));
            if (!active.isEmpty()) {
                AsyncTask task = active.get(0);
                String message = task.getStatusMessage() != null ? task.getStatusMessage() : task.getStatus().name();
                return new IndexProgress(
                        task.getProgress() >= 0 ? task.getProgress() : 0,
                        message,
                        false);
            }
            List<AsyncTask> recent = taskRepository.findByKindAndBusinessIdAndStatusIn(
                    TaskKind.CODEGRAPH_INDEX, projectSpaceId,
                    List.of(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED));
            if (!recent.isEmpty()) {
                AsyncTask task = recent.get(0);
                String message;
                int percent;
                if (task.getStatus() == TaskStatus.SUCCEEDED) {
                    message = "索引完成";
                    percent = 100;
                } else if (task.getStatus() == TaskStatus.CANCELLED) {
                    message = "索引已取消";
                    percent = 0;
                } else {
                    message = task.getErrorMessage() != null ? task.getErrorMessage() : "索引失败";
                    percent = 0;
                }
                return new IndexProgress(percent, message, true);
            }
        } catch (Exception e) {
            log.debug("从 asyncTasks 读取进度失败，projectSpaceId={}", projectSpaceId);
        }
        return new IndexProgress(0, "未开始", false);
    }

    /**
     * 清理指定项目空间的进度记录。
     */
    public void clear(Long projectSpaceId) {
        progressMap.remove(projectSpaceId);
    }

    /**
     * 索引进度数据。
     */
    public static class IndexProgress {
        private final int percent;
        private final String message;
        private final boolean completed;

        public IndexProgress(int percent, String message, boolean completed) {
            this.percent = percent;
            this.message = message;
            this.completed = completed;
        }

        public int getPercent() {
            return percent;
        }

        public String getMessage() {
            return message;
        }

        public boolean isCompleted() {
            return completed;
        }
    }
}
