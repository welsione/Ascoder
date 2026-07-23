package cn.welsione.ascoder.common.task;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务线程池注册表，按 TaskKind 隔离线程池。
 *
 * <p>每种 TaskKind 拥有独立的 ThreadPoolExecutor，配置来自硬编码默认值
 * （后续可接入 RuntimeSettings 热改）。</p>
 */
@Slf4j
@Component
public class TaskExecutorRegistry {

    private final Map<TaskKind, ThreadPoolExecutor> executors = new EnumMap<>(TaskKind.class);

    @PostConstruct
    void initExecutors() {
        register(TaskKind.GIT_CLONE, 1, 2, 4);
        register(TaskKind.GIT_FETCH, 2, 4, 8);
        register(TaskKind.CODEGRAPH_INDEX, 1, 1, 2);
        register(TaskKind.CODEGRAPH_SYNC, 1, 2, 4);
        register(TaskKind.PROJECT_SPACE_PREPARE, 1, 2, 4);
        register(TaskKind.BRANCH_REFRESH, 1, 2, 8);
        log.info("异步任务线程池初始化完成，共 {} 种", executors.size());
    }

    ExecutorService getExecutor(TaskKind kind) {
        ThreadPoolExecutor executor = executors.get(kind);
        if (executor == null) {
            throw new IllegalArgumentException("未注册的任务类型: " + kind);
        }
        return executor;
    }

    @PreDestroy
    void shutdown() {
        executors.forEach((kind, executor) -> {
            log.info("关闭 {} 线程池", kind);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        });
    }

    private void register(TaskKind kind, int core, int max, int queueCapacity) {
        AtomicInteger counter = new AtomicInteger(0);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                core, max, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "task-" + kind.name().toLowerCase() + "-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        executors.put(kind, executor);
    }
}
