package cn.welsione.ascoder.common.task;

import lombok.Value;

/**
 * 任务线程池参数（不可变数据载体）。
 */
@Value
public class TaskPoolParams {

    int coreThreads;
    int maxThreads;
    int queueCapacity;
}
