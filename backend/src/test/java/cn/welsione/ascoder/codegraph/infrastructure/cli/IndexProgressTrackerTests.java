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
        // DB 中无活跃任务，回退到内存
        assertEquals(0, p.getPercent());
        assertEquals("开始索引", p.getMessage());
        assertFalse(p.isCompleted());
    }

    @Test
    void updateSetsProgress() {
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(Collections.emptyList());

        tracker.update(100L, 50, "halfway");

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(50, p.getPercent());
        assertEquals("halfway", p.getMessage());
        assertFalse(p.isCompleted());
    }

    @Test
    void completeSetsHundredPercent() {
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(Collections.emptyList());

        tracker.complete(100L);

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(100, p.getPercent());
        assertEquals("索引完成", p.getMessage());
        assertTrue(p.isCompleted());
    }

    @Test
    void failSetsError() {
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(Collections.emptyList());

        tracker.fail(100L, "boom");

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(0, p.getPercent());
        assertEquals("boom", p.getMessage());
        assertTrue(p.isCompleted());
    }

    @Test
    void clearRemovesInMemoryProgress() {
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(Collections.emptyList());

        tracker.start(100L);
        tracker.clear(100L);

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(0, p.getPercent());
        assertEquals("未开始", p.getMessage());
        assertFalse(p.isCompleted());
    }

    // ---- DB 优先读取 ----

    @Test
    void getPrefersActiveTaskFromDb() {
        AsyncTask active = buildTask(TaskStatus.RUNNING, 60, "indexing", null);
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(
                eq(TaskKind.CODEGRAPH_INDEX), eq(100L),
                eq(List.of(TaskStatus.QUEUED, TaskStatus.RUNNING))))
                .thenReturn(List.of(active));

        tracker.update(100L, 10, "memory");

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(60, p.getPercent());
        assertEquals("indexing", p.getMessage());
        assertFalse(p.isCompleted());
    }

    @Test
    void getFallsBackToMemoryWhenNoActiveTask() {
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(
                eq(TaskKind.CODEGRAPH_INDEX), eq(100L),
                eq(List.of(TaskStatus.QUEUED, TaskStatus.RUNNING))))
                .thenReturn(Collections.emptyList());
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(
                eq(TaskKind.CODEGRAPH_INDEX), eq(100L),
                eq(List.of(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED))))
                .thenReturn(Collections.emptyList());

        tracker.update(100L, 30, "memory progress");

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals(30, p.getPercent());
        assertEquals("memory progress", p.getMessage());
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
    void getFallsBackToMemoryWhenDbThrows() {
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
    void getActiveTaskFallsBackToStatusNameWhenMessageNull() {
        AsyncTask active = buildTask(TaskStatus.RUNNING, 0, null, null);
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(any(), any(), anyList()))
                .thenReturn(List.of(active));

        IndexProgressTracker.IndexProgress p = tracker.get(100L);
        assertEquals("RUNNING", p.getMessage());
    }
}
