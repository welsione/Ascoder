package cn.welsione.ascoder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步事件执行配置。
 *
 * <p>提供自学习等业务事件的专用异步执行器，事件监听器可通过
 * {@code @Async("selfLearningEventExecutor")} 将长耗时或非关键处理移出事务提交线程，
 * 避免阻塞请求线程（如 SSE 回答完成事件推送）。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    /**
     * 自学习事件专用线程池：核心 2 / 最大 4 / 队列 100，拒绝策略为调用方线程执行（不丢事件）。
     */
    @Bean(name = "selfLearningEventExecutor")
    public Executor selfLearningEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("self-learning-event-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
