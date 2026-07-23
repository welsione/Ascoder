package cn.welsione.ascoder.common.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskExecutorRegistry 线程池注册、获取与关闭测试。
 */
class TaskExecutorRegistryTests {

    private TaskExecutorRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = new TaskExecutorRegistry();
        // 手动触发 @PostConstruct
        Method m = TaskExecutorRegistry.class.getDeclaredMethod("initExecutors");
        m.setAccessible(true);
        m.invoke(registry);
    }

    @Test
    void initRegistersAllTaskKinds() {
        for (TaskKind kind : TaskKind.values()) {
            assertNotNull(registry.getExecutor(kind));
        }
    }

    @Test
    void getExecutorReturnsNonClosedPool() {
        ExecutorService executor = registry.getExecutor(TaskKind.CODEGRAPH_INDEX);
        assertNotNull(executor);
        assertFalse(executor.isShutdown());
    }

    @Test
    void getExecutorThrowsForNullKind() {
        // EnumMap.get(null) 返回 null，代码抛 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> registry.getExecutor(null));
    }

    @Test
    void shutdownClosesAllPools() throws Exception {
        Method shutdown = TaskExecutorRegistry.class.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);
        shutdown.invoke(registry);

        for (TaskKind kind : TaskKind.values()) {
            assertTrue(registry.getExecutor(kind).isShutdown());
        }
    }
}
