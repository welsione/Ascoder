package cn.welsione.ascoder.common.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TaskExecutorRegistry 线程池注册、获取与关闭测试。
 *
 * <p>参数合法性校验由 {@link TaskPoolConfigPort} 实现负责，不在本测试范围内。</p>
 */
class TaskExecutorRegistryTests {

    private TaskPoolConfigPort poolConfig;
    private TaskExecutorRegistry registry;

    @BeforeEach
    void setUp() {
        poolConfig = mock(TaskPoolConfigPort.class);
        when(poolConfig.resolve(any(TaskKind.class))).thenReturn(new TaskPoolParams(1, 2, 4));
        registry = newRegistry();
    }

    @Test
    void initRegistersAllTaskKinds() {
        for (TaskKind kind : TaskKind.values()) {
            assertNotNull(registry.getExecutor(kind));
        }
    }

    @Test
    void initUsesParametersFromPoolConfig() {
        when(poolConfig.resolve(TaskKind.GIT_FETCH)).thenReturn(new TaskPoolParams(3, 5, 8));
        TaskExecutorRegistry fresh = newRegistry();
        assertNotNull(fresh.getExecutor(TaskKind.GIT_FETCH));
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
    void shutdownClosesAllPools() {
        ReflectionTestUtils.invokeMethod(registry, "shutdown");

        for (TaskKind kind : TaskKind.values()) {
            assertTrue(registry.getExecutor(kind).isShutdown());
        }
    }

    /** 重建注册表并触发初始化（模拟 @PostConstruct）。 */
    private TaskExecutorRegistry newRegistry() {
        TaskExecutorRegistry fresh = new TaskExecutorRegistry(poolConfig);
        ReflectionTestUtils.invokeMethod(fresh, "initExecutors");
        return fresh;
    }
}
