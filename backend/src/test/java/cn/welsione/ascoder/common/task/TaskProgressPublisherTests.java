package cn.welsione.ascoder.common.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskProgressPublisher 节流持久化与自定义事件测试。
 */
class TaskProgressPublisherTests {

    private AsyncTaskJpaRepository taskRepository;
    private TransactionTemplate txTemplate;
    private TaskProgressPublisher publisher;

    @BeforeEach
    void setUp() {
        taskRepository = mock(AsyncTaskJpaRepository.class);
        txTemplate = mock(TransactionTemplate.class);
        publisher = new TaskProgressPublisher(taskRepository, txTemplate);
    }

    /** 模拟 TransactionTemplate.executeWithoutResult，直接执行 Consumer 回调。 */
    @SuppressWarnings("unchecked")
    private void simulateTransaction() {
        doAnswer(inv -> {
            Consumer<Object> consumer = inv.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(txTemplate).executeWithoutResult(any(Consumer.class));
    }

    @Test
    void persistProgressWritesDbOnFirstCall() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> inv.getArgument(0));
        simulateTransaction();

        publisher.persistProgress(1L, 50, "halfway");

        verify(taskRepository).findById(1L);
        verify(taskRepository).save(any(AsyncTask.class));
        assertEquals(50, task.getProgress());
        assertEquals("halfway", task.getStatusMessage());
    }

    @Test
    void persistProgressThrottledWithinTwoSeconds() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> inv.getArgument(0));
        simulateTransaction();

        publisher.persistProgress(1L, 10, "first");
        publisher.persistProgress(1L, 20, "second");

        // 节流：第二次调用被跳过，save 只被调用一次
        verify(taskRepository, times(1)).save(any(AsyncTask.class));
    }

    @Test
    void persistProgressHundredNotThrottled() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> inv.getArgument(0));
        simulateTransaction();

        publisher.persistProgress(1L, 50, "halfway");
        publisher.persistProgress(1L, 100, "完成");

        // 100% 是终态进度，不受节流限制，必须写入
        verify(taskRepository, times(2)).save(any(AsyncTask.class));
        assertEquals(100, task.getProgress());
    }

    @Test
    void clearThrottleAllowsImmediateWrite() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> inv.getArgument(0));
        simulateTransaction();

        publisher.persistProgress(1L, 10, "first");
        publisher.clearThrottle(1L);
        publisher.persistProgress(1L, 20, "second");

        verify(taskRepository, times(2)).save(any(AsyncTask.class));
    }

    @Test
    void persistProgressWithNegativePercentDoesNotUpdateProgress() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setProgress(77);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> inv.getArgument(0));
        simulateTransaction();

        publisher.persistProgress(1L, -1, "still running");

        assertEquals(77, task.getProgress());
        assertEquals("still running", task.getStatusMessage());
    }

    @Test
    void persistProgressWithNullMessageDoesNotOverwriteMessage() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setStatusMessage("original");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> inv.getArgument(0));
        simulateTransaction();

        publisher.persistProgress(1L, 50, null);

        assertEquals(50, task.getProgress());
        assertEquals("original", task.getStatusMessage());
    }

    @Test
    void persistProgressSwallowsExceptionFromDb() {
        when(taskRepository.findById(1L)).thenThrow(new RuntimeException("db down"));
        // 不会抛出异常
        assertDoesNotThrow(() -> publisher.persistProgress(1L, 10, "msg"));
    }

    @Test
    void pushCustomEventDoesNotThrow() {
        assertDoesNotThrow(() -> publisher.pushCustomEvent(1L, "custom", Map.of("k", "v")));
    }
}
