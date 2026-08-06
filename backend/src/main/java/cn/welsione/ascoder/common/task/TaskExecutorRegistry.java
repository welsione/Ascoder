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
 * <p>每种 TaskKind 拥有独立的 ThreadPoolExecutor，参数由 {@link TaskPoolConfigPort}
 * 提供（基于运行时设置，可在设置页修改，修改需重启进程生效）。
 * 参数合法性（core≥1、max≥core、queue≥1）由端口实现负责校验，非法配置在启动时快速失败。</p>
 */
@Slf4j
@Component
public class TaskExecutorRegistry {

    private final TaskPoolConfigPort poolConfig;

    public TaskExecutorRegistry(TaskPoolConfigPort poolConfig) {
        this.poolConfig = poolConfig;
    }

    private final Map<TaskKind, ThreadPoolExecutor> executors = new EnumMap<>(TaskKind.class);

    @PostConstruct
    void initExecutors() {
        for (TaskKind kind : TaskKind.values()) {
            TaskPoolParams params = poolConfig.resolve(kind);
            register(kind, params.getCoreThreads(), params.getMaxThreads(), params.getQueueCapacity());
        }
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
