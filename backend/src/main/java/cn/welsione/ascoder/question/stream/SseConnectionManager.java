package cn.welsione.ascoder.question.stream;

import cn.welsione.ascoder.runtime.application.RuntimeSettingsService;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * SSE 连接管理，负责 emitter 的创建、心跳维持、过期巡检与统一回收。
 *
 * <p>持有独立的推理线程池和心跳调度器，所有 emitter 通过 {@link #closeEmitter(Long)} 统一释放，
 * 避免回调缺失导致的内存泄漏。</p>
 *
 * <p>{@code sse-timeout-seconds} 与 {@code heartbeat-interval-seconds} 在
 * 设置页修改后，<b>只对后续新建的 emitter 生效</b>；线程池相关参数（core / max / queue）
 * 需要重启进程生效。</p>
 */
@Slf4j
@Component
class SseConnectionManager {

    private static final long SWEEP_INTERVAL_SECONDS = 60;

    private final Map<Long, EmitterEntry> emitters = new ConcurrentHashMap<>();
    final ExecutorService agentExecutor;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    private final RuntimeSettingsService runtimeSettings;

    SseConnectionManager(RuntimeSettingsService runtimeSettings) {
        this.runtimeSettings = runtimeSettings;
        int coreThreads = runtimeSettings.readInt("agent.stream-core-threads");
        int maxThreads = runtimeSettings.readInt("agent.stream-max-threads");
        int queueCapacity = runtimeSettings.readInt("agent.stream-queue-capacity");
        if (coreThreads < 1 || maxThreads < coreThreads || queueCapacity < 1) {
            throw new IllegalArgumentException("Agent 流式执行池配置非法");
        }
        this.agentExecutor = new ThreadPoolExecutor(
                coreThreads, maxThreads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread t = new Thread(runnable, "agent-stream-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        heartbeatScheduler.scheduleAtFixedRate(
                this::sweepStaleEmitters,
                SWEEP_INTERVAL_SECONDS,
                SWEEP_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private long sseTimeoutMillis() {
        return runtimeSettings.readInt("agent.sse-timeout-seconds") * 1000L;
    }

    private long heartbeatIntervalMillis() {
        return runtimeSettings.readInt("agent.heartbeat-interval-seconds") * 1000L;
    }

    SseEmitter createEmitter(Long questionId) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMillis());
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(questionId),
                heartbeatIntervalMillis(),
                heartbeatIntervalMillis(),
                TimeUnit.MILLISECONDS
        );
        EmitterEntry entry = new EmitterEntry(emitter, heartbeat, System.currentTimeMillis());
        EmitterEntry previous = emitters.put(questionId, entry);
        if (previous != null) {
            previous.dispose();
        }

        emitter.onCompletion(() -> {
            log.debug("SSE 连接完成，questionId={}", questionId);
            removeAndDispose(questionId);
        });
        emitter.onError(ex -> {
            log.warn("SSE 连接错误，questionId={}，错误={}", questionId, ex.getMessage());
            removeAndDispose(questionId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时，questionId={}", questionId);
            removeAndDispose(questionId);
        });

        return emitter;
    }

    void sendEvent(Long questionId, String eventName, Object data) {
        EmitterEntry entry = emitters.get(questionId);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (!emitters.containsKey(questionId)) {
                return;
            }
            try {
                entry.emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception ex) {
                log.warn("SSE 事件发送失败，questionId={}，事件={}，原因={}", questionId, eventName, ex.getMessage());
                removeAndDispose(questionId);
            }
        }
    }

    void closeEmitter(Long questionId) {
        EmitterEntry entry = emitters.remove(questionId);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            try {
                entry.emitter.complete();
            } catch (Exception ex) {
                log.debug("关闭 SSE emitter 失败，questionId={}", questionId);
            } finally {
                entry.dispose();
            }
        }
    }

    private void sendHeartbeat(Long questionId) {
        EmitterEntry entry = emitters.get(questionId);
        if (entry != null) {
            sendEvent(questionId, "heartbeat", Map.of("timestamp", System.currentTimeMillis()));
        }
    }

    private void sweepStaleEmitters() {
        long now = System.currentTimeMillis();
        emitters.forEach((id, entry) -> {
            if (now - entry.createdAt > sseTimeoutMillis() * 2L) {
                log.warn("巡检发现过期 SSE emitter，强制清理，questionId={}", id);
                removeAndDispose(id);
            }
        });
    }

    private void removeAndDispose(Long questionId) {
        EmitterEntry entry = emitters.remove(questionId);
        if (entry != null) {
            synchronized (entry) {
                entry.dispose();
            }
        }
    }

    @PreDestroy
    void shutdownExecutors() {
        emitters.keySet().forEach(this::closeEmitter);
        agentExecutor.shutdown();
        heartbeatScheduler.shutdown();
        try {
            if (!agentExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                agentExecutor.shutdownNow();
            }
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class EmitterEntry {
        final SseEmitter emitter;
        final ScheduledFuture<?> heartbeat;
        final long createdAt;

        EmitterEntry(SseEmitter emitter, ScheduledFuture<?> heartbeat, long createdAt) {
            this.emitter = emitter;
            this.heartbeat = heartbeat;
            this.createdAt = createdAt;
        }

        void dispose() {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
        }
    }
}
