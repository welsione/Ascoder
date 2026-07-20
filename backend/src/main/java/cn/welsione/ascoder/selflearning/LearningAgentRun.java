package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * Self Learning Agent 后台整理运行记录，用于追踪任务状态、进度和失败原因。
 */
@Entity
@Table(name = "learningAgentRuns")
@Getter
@Setter
@NoArgsConstructor
public class LearningAgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    private ProjectSpace projectSpace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningAgentRunStatus status = LearningAgentRunStatus.QUEUED;

    private int limitCount = 12;
    private int createdInsightCount;
    private int consumedRawEventCount;
    private int skippedRawEventCount;
    private int failedConversationCount;

    @Column(columnDefinition = "text")
    private String currentRawEventIdsJson;

    @Column(columnDefinition = "mediumtext")
    private String failureDetailsJson;

    @Column(columnDefinition = "text")
    private String message;

    @Column(columnDefinition = "mediumtext")
    private String errorMessage;

    private Date startedAt;
    private Date finishedAt;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    @Version
    private Long version = 0L;

    public void start() {
        status = LearningAgentRunStatus.RUNNING;
        startedAt = new Date();
        touch();
    }

    public void progress(int createdCount, int consumedCount, int failedCount, String rawEventIdsJson, String failuresJson) {
        createdInsightCount = createdCount;
        consumedRawEventCount = consumedCount;
        failedConversationCount = failedCount;
        currentRawEventIdsJson = rawEventIdsJson;
        failureDetailsJson = failuresJson;
        touch();
    }

    public void complete(LearningAgentRunStatus nextStatus, SelfLearningAgentRunResponse response) {
        status = nextStatus;
        createdInsightCount = response.getCreatedInsightCount();
        consumedRawEventCount = response.getConsumedRawEventCount();
        skippedRawEventCount = response.getSkippedRawEventCount();
        failedConversationCount = response.getFailedConversationCount();
        message = response.getMessage();
        finishedAt = new Date();
        touch();
    }

    public void fail(String error) {
        status = LearningAgentRunStatus.FAILED;
        errorMessage = error;
        message = "Self Learning Agent 后台整理失败。";
        finishedAt = new Date();
        touch();
    }

    public void touch() {
        updatedAt = new Date();
    }
}
