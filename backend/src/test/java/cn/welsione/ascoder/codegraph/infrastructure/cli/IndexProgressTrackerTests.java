package cn.welsione.ascoder.codegraph.infrastructure.cli;

import cn.welsione.ascoder.common.task.AsyncTask;
import cn.welsione.ascoder.common.task.AsyncTaskJpaRepository;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IndexProgressTracker 内存进度与 DB 回退策略测试。
 */
class IndexProgressTrackerTests {

    private AsyncTaskJpaRepository taskRepository;
    private IndexProgressTracker tracker;

    @BeforeEach
    void setUp() {
        taskRepository = mock(AsyncTaskJpaRepository.class);
        tracker = new IndexProgressTracker(taskRepository);
    }

    private AsyncTask buildTask(TaskStatus status, int progress, String message, String error) {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setKind(TaskKind.CODEGRAPH_INDEX);
        task.setStatus(status);
        task.setProgress(progress);
        task.setStatusMessage(message);
        task.setErrorMessage(error);
        return task;
    }

    // ---- 内存进度更新 ----

    @Test
    void startSetsInitialProgress() {
        tracker.start(100L);

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        // 内存优先，start 写入内存
        assertEquals(0, p.getPercent());
        assertEquals("开始索引", p.getMessage());
        assertFalse(p.isCompleted());
    }

    @Test
    void updateSetsProgress() {
        tracker.update(100L, 50, "halfway");

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(50, p.getPercent());
        assertEquals("halfway", p.getMessage());
        assertFalse(p.isCompleted());
    }

    @Test
    void completeSetsHundredPercent() {
        tracker.complete(100L);

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(100, p.getPercent());
        assertEquals("索引完成", p.getMessage());
        assertTrue(p.isCompleted());
    }

    @Test
    void failSetsError() {
        tracker.fail(100L, "boom");

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(0, p.getPercent());
        assertEquals("boom", p.getMessage());
        assertTrue(p.isCompleted());
    }

    @Test
    void clearRemovesInMemoryProgress() {
        tracker.start(100L);
        tracker.clear(100L);

        // 内存被清除，DB 也无记录，返回默认值
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(Collections.emptyList());
        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(0, p.getPercent());
        assertEquals("未开始", p.getMessage());
        assertFalse(p.isCompleted());
    }

    // ---- 内存优先读取 ----

    @Test
    void getPrefersMemoryOverDb() {
        // 内存中有 CLI 实时写入的进度，DB 中有旧值
        AsyncTask active = buildTask(TaskStatus.RUNNING, 60, "indexing", null);
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(
                eq(TaskKind.CODEGRAPH_INDEX), eq(100L),
                eq(List.of(TaskStatus.QUEUED, TaskStatus.RUNNING))))
                .thenReturn(List.of(active));

        tracker.update(100L, 10, "memory");

        // 内存优先，应返回内存中的值而非 DB 中的旧值
        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(10, p.getPercent());
        assertEquals("memory", p.getMessage());
        assertFalse(p.isCompleted());
    }

    @Test
    void getFallsBackToDbWhenNoMemoryProgress() {
        // 内存无记录（重启恢复场景），从 DB 读取
        AsyncTask active = buildTask(TaskStatus.RUNNING, 30, "from db", null);
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(
                eq(TaskKind.CODEGRAPH_INDEX), eq(100L),
                eq(List.of(TaskStatus.QUEUED, TaskStatus.RUNNING))))
                .thenReturn(List.of(active));

        // 不调用 tracker.update/start，内存无记录
        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(30, p.getPercent());
        assertEquals("from db", p.getMessage());
        assertFalse(p.isCompleted());
    }

    @Test
    void getReturnsTerminalProgressForSucceededTask() {
        AsyncTask done = buildTask(TaskStatus.SUCCEEDED, 100, null, null);
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(done));

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(100, p.getPercent());
        assertEquals("索引完成", p.getMessage());
        assertTrue(p.isCompleted());
    }

    @Test
    void getReturnsTerminalProgressForFailedTask() {
        AsyncTask done = buildTask(TaskStatus.FAILED, 0, null, "connection refused");
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(done));

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(0, p.getPercent());
        assertEquals("connection refused", p.getMessage());
        assertTrue(p.isCompleted());
    }

    @Test
    void getReturnsTerminalProgressForCancelledTask() {
        AsyncTask done = buildTask(TaskStatus.CANCELLED, 0, null, null);
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(done));

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(0, p.getPercent());
        assertEquals("索引已取消", p.getMessage());
        assertTrue(p.isCompleted());
    }

    @Test
    void getReturnsMemoryWhenDbThrows() {
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenThrow(new RuntimeException("db down"));

        tracker.update(100L, 42, "from memory");

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(42, p.getPercent());
        assertEquals("from memory", p.getMessage());
        assertFalse(p.isCompleted());
    }

    @Test
    void getReturnsDefaultWhenNoDataAnywhere() {
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(Collections.emptyList());

        IndexProgressTracker.IndexProgress p = tracker.get(999L);
        assertEquals(0, p.getPercent());
        assertEquals("未开始", p.getMessage());
        assertFalse(p.isCompleted());
    }

    @Test
    void getDbFallbackUsesStatusNameWhenMessageNull() {
        // 内存无记录，从 DB 读取，message 为 null 时回退到 status name
        AsyncTask active = buildTask(TaskStatus.RUNNING, 0, null, null);
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(List.of(active));

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals("RUNNING", p.getMessage());
    }
}
