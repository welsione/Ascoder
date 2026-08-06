package cn.welsione.ascoder.runtime.application;

import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskPoolParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RuntimeTaskPoolConfigProvider 配置读取、kind 映射与参数校验测试。
 */
class RuntimeTaskPoolConfigProviderTests {

    private RuntimeSettingsService runtimeSettings;
    private RuntimeTaskPoolConfigProvider provider;

    @BeforeEach
    void setUp() {
        runtimeSettings = mock(RuntimeSettingsService.class);
        provider = new RuntimeTaskPoolConfigProvider(runtimeSettings);
    }

    @Test
    void resolveReadsAllThreeKeysWithKebabCaseMapping() {
        when(runtimeSettings.readInt("task.git-clone-core-threads")).thenReturn(1);
        when(runtimeSettings.readInt("task.git-clone-max-threads")).thenReturn(2);
        when(runtimeSettings.readInt("task.git-clone-queue-capacity")).thenReturn(4);

        TaskPoolParams params = provider.resolve(TaskKind.GIT_CLONE);

        assertEquals(1, params.getCoreThreads());
        assertEquals(2, params.getMaxThreads());
        assertEquals(4, params.getQueueCapacity());
        verify(runtimeSettings).readInt(eq("task.git-clone-core-threads"));
        verify(runtimeSettings).readInt(eq("task.git-clone-max-threads"));
        verify(runtimeSettings).readInt(eq("task.git-clone-queue-capacity"));
    }

    @Test
    void resolveMapsProjectSpaceKindKey() {
        when(runtimeSettings.readInt("task.project-space-prepare-core-threads")).thenReturn(1);
        when(runtimeSettings.readInt("task.project-space-prepare-max-threads")).thenReturn(2);
        when(runtimeSettings.readInt("task.project-space-prepare-queue-capacity")).thenReturn(4);

        provider.resolve(TaskKind.PROJECT_SPACE_PREPARE);

        verify(runtimeSettings).readInt(eq("task.project-space-prepare-max-threads"));
    }

    @Test
    void resolveRejectsCoreBelowOne() {
        when(runtimeSettings.readInt("task.git-fetch-core-threads")).thenReturn(0);
        when(runtimeSettings.readInt("task.git-fetch-max-threads")).thenReturn(4);
        when(runtimeSettings.readInt("task.git-fetch-queue-capacity")).thenReturn(8);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.resolve(TaskKind.GIT_FETCH));
        assertTrue(ex.getMessage().contains("core=0"));
    }

    @Test
    void resolveRejectsMaxBelowCore() {
        when(runtimeSettings.readInt("task.git-fetch-core-threads")).thenReturn(4);
        when(runtimeSettings.readInt("task.git-fetch-max-threads")).thenReturn(2);
        when(runtimeSettings.readInt("task.git-fetch-queue-capacity")).thenReturn(8);

        assertThrows(IllegalStateException.class, () -> provider.resolve(TaskKind.GIT_FETCH));
    }

    @Test
    void resolveRejectsZeroQueueCapacity() {
        when(runtimeSettings.readInt("task.git-fetch-core-threads")).thenReturn(1);
        when(runtimeSettings.readInt("task.git-fetch-max-threads")).thenReturn(2);
        when(runtimeSettings.readInt("task.git-fetch-queue-capacity")).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> provider.resolve(TaskKind.GIT_FETCH));
    }
}
