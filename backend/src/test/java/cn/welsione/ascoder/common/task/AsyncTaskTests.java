package cn.welsione.ascoder.common.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AsyncTask 实体状态转换与默认值测试。
 */
class AsyncTaskTests {

    private AsyncTask task;

    @BeforeEach
    void setUp() {
        task = new AsyncTask();
        task.setId(1L);
        task.setKind(TaskKind.GIT_CLONE);
    }

    @Test
    void defaultValues() {
        assertEquals(TaskStatus.QUEUED, task.getStatus());
        assertEquals(-1, task.getProgress());
        assertEquals(0, task.getMaxRetries());
        assertEquals(0L, task.getVersion());
    }

    @Test
    void startSetsRunningAndStartedAt() {
        task.start();

        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertNotNull(task.getStartedAt());
    }

    @Test
    void succeedSetsSucceededAndResult() {
        task.start();
        task.succeed("{\"rows\":10}");

        assertEquals(TaskStatus.SUCCEEDED, task.getStatus());
        assertEquals("{\"rows\":10}", task.getResultJson());
        assertNotNull(task.getFinishedAt());
    }

    @Test
    void succeedSetsProgressTo100() {
        task.start();
        task.updateProgress(50, "处理中");
        task.succeed(null);

        // 成功后进度必须为 100，即使之前是中间值
        assertEquals(100, task.getProgress());
    }

    @Test
    void failSetsFailedAndError() {
        task.start();
        task.fail("boom");

        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals("boom", task.getErrorMessage());
        assertNotNull(task.getFinishedAt());
    }

    @Test
    void cancelSetsCancelledAndFinishedAt() {
        task.start();
        task.cancel();

        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertNotNull(task.getFinishedAt());
    }

    @Test
    void updateProgressSetsPercentAndMessage() {
        task.updateProgress(42, "processing");

        assertEquals(42, task.getProgress());
        assertEquals("processing", task.getStatusMessage());
    }

    @Test
    void isTerminalTrueForTerminalStatuses() {
        task.setStatus(TaskStatus.SUCCEEDED);
        assertTrue(task.isTerminal());

        task.setStatus(TaskStatus.FAILED);
        assertTrue(task.isTerminal());

        task.setStatus(TaskStatus.CANCELLED);
        assertTrue(task.isTerminal());
    }

    @Test
    void isTerminalFalseForNonTerminalStatuses() {
        task.setStatus(TaskStatus.QUEUED);
        assertFalse(task.isTerminal());

        task.setStatus(TaskStatus.RUNNING);
        assertFalse(task.isTerminal());
    }
}
