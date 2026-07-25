package cn.welsione.ascoder.common.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 异步任务视图对象，在列表查询时附加 businessLabel 等前端展示信息。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTaskView {

    private Long id;
    private TaskKind kind;
    private TaskStatus status;
    private Long businessId;
    /** businessId 的可读标签，如 "ascoder (仓库)"。 */
    private String businessLabel;
    private int progress;
    private String statusMessage;
    private String errorMessage;
    private int maxRetries;
    private int retryCount;
    private long timeoutMs;
    private Date queuedAt;
    private Date startedAt;
    private Date finishedAt;
    private Date createdAt;
    private Date updatedAt;

    /**
     * 从 AsyncTask 实体构建视图，附加 businessLabel。
     */
    public static AsyncTaskView from(AsyncTask task, String businessLabel) {
        return new AsyncTaskView(
                task.getId(),
                task.getKind(),
                task.getStatus(),
                task.getBusinessId(),
                businessLabel,
                task.getProgress(),
                task.getStatusMessage(),
                task.getErrorMessage(),
                task.getMaxRetries(),
                task.getRetryCount(),
                task.getTimeoutMs(),
                task.getQueuedAt(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
