package cn.welsione.ascoder.common.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 异步任务提交请求。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmitRequest<C> {

    /** 任务类型。 */
    private TaskKind kind;

    /** 业务上下文。 */
    private C context;

    /** 业务关联 ID（如 repositoryId / projectSpaceId），用于去重和查询。 */
    private Long businessId;

    /** 最大重试次数（默认 0，不重试）。 */
    private int maxRetries = 0;

    /** 重试间隔毫秒（默认 5000）。 */
    private long retryIntervalMs = 5000;

    /** 任务超时毫秒（0 表示不限时，由 TaskKind 级别配置兜底）。 */
    private long timeoutMs = 0;
}
