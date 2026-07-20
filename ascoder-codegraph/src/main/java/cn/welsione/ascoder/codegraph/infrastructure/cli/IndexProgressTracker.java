package cn.welsione.ascoder.codegraph.infrastructure.cli;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CodeGraph 索引进度跟踪器，在内存中维护各项目空间的索引进度。
 */
@Slf4j
public class IndexProgressTracker {

    private final Map<Long, IndexProgress> progressMap = new ConcurrentHashMap<>();

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
        log.info("项目空间 {} 索引进度: {}% - {}", projectSpaceId, percent, message);
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
     */
    public IndexProgress get(Long projectSpaceId) {
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
