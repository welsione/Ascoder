package cn.welsione.ascoder.common.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TaskExecutorRegistry 线程池注册、获取、关闭与参数校验测试。
 */
class TaskExecutorRegistryTests {

    private TaskPoolConfigPort poolConfig;
    private TaskExecutorRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
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
    void initRejectsCoreBelowOne() {
        assertInitRejected(new TaskPoolParams(0, 2, 4));
    }

    @Test
    void initRejectsMaxBelowCore() {
        assertInitRejected(new TaskPoolParams(4, 2, 8));
    }

    @Test
    void initRejectsZeroQueueCapacity() {
        assertInitRejected(new TaskPoolParams(1, 2, 0));
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

    /** 用默认 mock 参数重建注册表并触发初始化。 */
    private TaskExecutorRegistry newRegistry() {
        TaskExecutorRegistry fresh = new TaskExecutorRegistry(poolConfig);
        invokeInit(fresh);
        return fresh;
    }

    /** 非法参数下初始化应抛 IllegalArgumentException。 */
    private void assertInitRejected(TaskPoolParams params) {
        when(poolConfig.resolve(any(TaskKind.class))).thenReturn(params);
        TaskExecutorRegistry fresh = new TaskExecutorRegistry(poolConfig);
        assertThrows(IllegalArgumentException.class, () -> invokeInit(fresh));
    }

    private void invokeInit(TaskExecutorRegistry target) {
        try {
            Method m = TaskExecutorRegistry.class.getDeclaredMethod("initExecutors");
            m.setAccessible(true);
            m.invoke(target);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
