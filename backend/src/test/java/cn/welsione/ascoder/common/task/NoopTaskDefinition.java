package cn.welsione.ascoder.common.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Map;

/**
 * 测试专用 TaskDefinition：不依赖外部资源，behavior 可动态配置。
 *
 * <p>用 {@link TaskKind#BRANCH_REFRESH}（线程池队列大，适合并发测试），
 * {@code @Order(LOWEST_PRECEDENCE)} 确保在 TaskEngine 注册时覆盖生产的
 * {@code BranchRefreshTaskDefinition}（同 kind）。</p>
 */
@TestComponent
@Order(Ordered.LOWEST_PRECEDENCE)
public class NoopTaskDefinition implements TaskDefinition<Map<String, String>> {

    private static final TypeReference<Map<String, String>> CONTEXT_TYPE = new TypeReference<>() {};
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 可动态配置的任务行为（成功/失败/取消/阻塞）。 */
    public volatile ThrowingConsumer<Map<String, String>> behavior = ctx -> {};

    @Override
    public TaskKind kind() {
        return TaskKind.BRANCH_REFRESH;
    }

    @Override
    public void execute(Map<String, String> context, TaskProgress progress) throws Exception {
        behavior.accept(context);
    }

    @Override
    public String serializeContext(Map<String, String> context) {
        try {
            return MAPPER.writeValueAsString(context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, String> deserializeContext(String json) {
        try {
            return MAPPER.readValue(json, CONTEXT_TYPE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 允许抛 Exception 的 Consumer，便于在 behavior 中写 Thread.sleep 等。 */
    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }
}
