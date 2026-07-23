package cn.welsione.ascoder.common.task;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务进度回调实现，由引擎注入给 TaskDefinition.execute()。
 */
class TaskProgressImpl implements TaskProgress {

    private final Long taskId;
    private final TaskProgressPublisher publisher;
    private final AtomicBoolean cancelled;

    TaskProgressImpl(Long taskId, TaskProgressPublisher publisher, AtomicBoolean cancelled) {
        this.taskId = taskId;
        this.publisher = publisher;
        this.cancelled = cancelled;
    }

    @Override
    public void percent(int value) {
        publisher.persistProgress(taskId, value, null);
    }

    @Override
    public void message(String messageText) {
        publisher.persistProgress(taskId, -1, messageText);
    }

    @Override
    public void update(int percentValue, String messageText) {
        publisher.persistProgress(taskId, percentValue, messageText);
    }

    @Override
    public void pushEvent(String eventName, Map<String, Object> payload) {
        publisher.pushCustomEvent(taskId, eventName, payload);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    void markCancelled() {
        cancelled.set(true);
    }
}
