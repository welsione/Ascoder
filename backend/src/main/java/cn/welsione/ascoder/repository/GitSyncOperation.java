package cn.welsione.ascoder.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Git 同步操作类型，用于 {@link cn.welsione.ascoder.repository.task.GitFetchTaskDefinition} 上下文中的 operation 字段。
 *
 * <p>序列化为字符串存入任务上下文，由
 * {@link cn.welsione.ascoder.repository.task.GitFetchTaskDefinition#execute} 读取后决定执行 fetch 还是 pull。</p>
 */
public enum GitSyncOperation {

    /** 仅同步远端引用，不修改工作区。 */
    FETCH("fetch"),

    /** 拉取并合并到当前分支。 */
    PULL("pull");

    private final String code;

    GitSyncOperation(String code) {
        this.code = code;
    }

    /** 返回存入任务上下文的字符串编码。 */
    @JsonValue
    public String code() {
        return code;
    }

    /**
     * 从任务上下文中读取的字符串编码解析为枚举。
     *
     * @param code 上下文中的 operation 值，可为 null
     * @return 对应的枚举值；code 为 null 时返回 {@link #FETCH}（向后兼容旧任务）
     * @throws IllegalArgumentException code 不是 fetch/pull 之一
     */
    @JsonCreator
    public static GitSyncOperation fromCode(String code) {
        if (code == null) {
            return FETCH;
        }
        for (GitSyncOperation operation : values()) {
            if (operation.code.equals(code)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("未知的 Git 同步操作类型：" + code);
    }
}
