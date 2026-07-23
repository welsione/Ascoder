package cn.welsione.ascoder.common.task;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 异步任务实体，持久化任务生命周期状态、进度和上下文。
 *
 * <p>与各业务实体（CodeRepository / ProjectSpace 等）通过 businessId 关联，
 * 不建立 JPA 外键关系，符合跨聚合约束。</p>
 */
@Entity
@Table(name = "asyncTasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private TaskKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status = TaskStatus.QUEUED;

    private Long businessId;

    @Column(columnDefinition = "mediumtext")
    private String contextJson;

    @Column(nullable = false)
    private int progress = -1;

    @Column(columnDefinition = "text")
    private String statusMessage;

    @Column(columnDefinition = "mediumtext")
    private String errorMessage;

    @Column(columnDefinition = "mediumtext")
    private String resultJson;

    @Column(nullable = false)
    private int maxRetries = 0;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private long timeoutMs = 0;

    @Column(nullable = false)
    private Date queuedAt = new Date();

    private Date startedAt;

    private Date finishedAt;

    @Column(nullable = false, updatable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    @Version
    private Long version = 0L;

    /** 标记任务开始执行。 */
    public void start() {
        this.status = TaskStatus.RUNNING;
        this.startedAt = new Date();
        this.updatedAt = new Date();
    }

    /** 标记任务成功完成。 */
    public void succeed(String result) {
        this.status = TaskStatus.SUCCEEDED;
        this.resultJson = result;
        this.finishedAt = new Date();
        this.updatedAt = new Date();
    }

    /** 标记任务失败。 */
    public void fail(String error) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = error;
        this.finishedAt = new Date();
        this.updatedAt = new Date();
    }

    /** 标记任务取消。 */
    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        this.finishedAt = new Date();
        this.updatedAt = new Date();
    }

    /** 更新进度。 */
    public void updateProgress(int percent, String message) {
        this.progress = percent;
        this.statusMessage = message;
        this.updatedAt = new Date();
    }

    /** 是否处于终态。 */
    public boolean isTerminal() {
        return status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED || status == TaskStatus.CANCELLED;
    }
}
