package cn.welsione.ascoder.common.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务进度推送，负责将进度更新持久化到 DB。
 *
 * <p>DB 更新采用"节流"策略：最小间隔 2 秒，避免高频进度更新导致写入压力。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProgressPublisher {

    private static final long MIN_PERSIST_INTERVAL_MS = 2000L;

    private final AsyncTaskJpaRepository taskRepository;
    private final TransactionTemplate txTemplate;

    /** 每个 taskId 上次持久化时间，用于节流。 */
    private final ConcurrentHashMap<Long, Long> lastPersistTime = new ConcurrentHashMap<>();

    /**
     * 持久化进度更新（节流：至少间隔 2s 才写 DB）。
     *
     * @param taskId  任务 ID
     * @param percent 百分比（-1 表示不变）
     * @param message 文本状态（null 表示不变）
     */
    void persistProgress(Long taskId, int percent, String message) {
        long now = System.currentTimeMillis();
        Long lastTime = lastPersistTime.get(taskId);
        if (lastTime != null && now - lastTime < MIN_PERSIST_INTERVAL_MS) {
            return; // 节流：距上次写入不足 2s，跳过
        }
        lastPersistTime.put(taskId, now);

        try {
            txTemplate.executeWithoutResult(status -> {
                taskRepository.findById(taskId).ifPresent(task -> {
                    if (percent >= 0) {
                        task.setProgress(percent);
                    }
                    if (message != null) {
                        task.setStatusMessage(message);
                    }
                    task.setUpdatedAt(new Date());
                    taskRepository.save(task);
                });
            });
        } catch (Exception e) {
            log.debug("进度持久化失败，taskId={}，错误={}", taskId, e.getMessage());
        }
    }

    /**
     * SSE 推送自定义事件。当前为空操作，后续接入 TaskSseManager 后实现。
     */
    void pushCustomEvent(Long taskId, String eventName, Map<String, Object> payload) {
        // TODO: 接入 TaskSseManager 后实现 SSE 推送
    }

    /** 进度持久化完成后清除节流记录。 */
    void clearThrottle(Long taskId) {
        lastPersistTime.remove(taskId);
    }
}
