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
     */
    public void update(Long projectSpaceId, int percent, String message) {
        progressMap.put(projectSpaceId, new IndexProgress(percent, message, false));
        log.debug("项目空间 {} 索引进度: {}% - {}", projectSpaceId, percent, message);
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
     * <p>优先从 asyncTasks 表读取（持久化进度），内存进度作为后备。</p>
     */
    public IndexProgress get(Long projectSpaceId) {
        try {
            // 优先查活跃任务（QUEUED/RUNNING）
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
            // 查最近完成的任务
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
            log.debug("从 asyncTasks 读取进度失败，回退到内存进度，projectSpaceId={}", projectSpaceId);
        }
        return progressMap.getOrDefault(projectSpaceId, new IndexProgress(0, "未开始", false));
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
