package cn.welsione.ascoder.runtime.application;

import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskPoolConfigPort;
import cn.welsione.ascoder.common.task.TaskPoolParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 基于运行时设置的异步任务线程池参数提供者。
 *
 * <p>从 {@link RuntimeSettingsService} 白名单读取 {@code task.<kind>-core-threads / -max-threads / -queue-capacity}，
 * 实现 {@link TaskPoolConfigPort} 供 common 模块的任务引擎消费。</p>
 */
@Component
@RequiredArgsConstructor
public class RuntimeTaskPoolConfigProvider implements TaskPoolConfigPort {

    private static final String KEY_PREFIX = "task.";
    private static final String CORE_SUFFIX = "-core-threads";
    private static final String MAX_SUFFIX = "-max-threads";
    private static final String QUEUE_SUFFIX = "-queue-capacity";

    private static final Map<TaskKind, String> KIND_KEYS = new EnumMap<>(TaskKind.class);
    static {
        KIND_KEYS.put(TaskKind.GIT_CLONE, "git-clone");
        KIND_KEYS.put(TaskKind.GIT_FETCH, "git-fetch");
        KIND_KEYS.put(TaskKind.CODEGRAPH_INDEX, "codegraph-index");
        KIND_KEYS.put(TaskKind.CODEGRAPH_SYNC, "codegraph-sync");
        KIND_KEYS.put(TaskKind.PROJECT_SPACE_PREPARE, "project-space-prepare");
        KIND_KEYS.put(TaskKind.BRANCH_REFRESH, "branch-refresh");
    }

    private final RuntimeSettingsService runtimeSettings;

    @Override
    public TaskPoolParams resolve(TaskKind kind) {
        String prefix = KEY_PREFIX + kindKey(kind);
        int core = runtimeSettings.readInt(prefix + CORE_SUFFIX);
        int max = runtimeSettings.readInt(prefix + MAX_SUFFIX);
        int queue = runtimeSettings.readInt(prefix + QUEUE_SUFFIX);
        return new TaskPoolParams(core, max, queue);
    }

    /**
     * TaskKind 到白名单 key 段的映射（kebab-case）。
     */
    private String kindKey(TaskKind kind) {
        String key = KIND_KEYS.get(kind);
        if (key == null) {
            throw new IllegalArgumentException("未注册线程池配置的任务类型: " + kind);
        }
        return key;
    }
}
