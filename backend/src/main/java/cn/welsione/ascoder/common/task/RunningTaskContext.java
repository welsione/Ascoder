package cn.welsione.ascoder.common.task;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 运行中任务的内存上下文，持有取消信号和 Future 引用。
 */
class RunningTaskContext {

    private final Future<?> future;
    private final TaskDefinition<?> definition;
    private final Object context;
    private final AtomicBoolean cancelled;
    private final TaskProgressImpl progress;

    RunningTaskContext(Future<?> future, TaskDefinition<?> definition, Object context, TaskProgressImpl progress) {
        this.future = future;
        this.definition = definition;
        this.context = context;
        this.progress = progress;
        this.cancelled = new AtomicBoolean(false);
    }

    @SuppressWarnings("unchecked")
    <C> void requestCancel() {
        cancelled.set(true);
        progress.markCancelled();
        ((TaskDefinition<C>) definition).onCancel((C) context);
        future.cancel(true);
    }

    boolean isCancelled() {
        return cancelled.get();
    }

    TaskProgressImpl getProgress() {
        return progress;
    }

    Future<?> getFuture() {
        return future;
    }
}
