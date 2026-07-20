package cn.welsione.ascoder.loganalysis.domain;

import cn.welsione.ascoder.question.domain.Question;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 日志分析任务，关联问题与日志上传，记录最终分析结果。
 */
@Entity
@Table(name = "logAnalysisTasks")
@Getter
@Setter
@NoArgsConstructor
public class LogAnalysisTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questionId", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    private ProjectSpace projectSpace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploadId", nullable = false)
    private LogUpload upload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LogAnalysisTaskStatus status = LogAnalysisTaskStatus.PENDING;

    @Column(columnDefinition = "mediumtext")
    private String summaryJson;

    @Column(columnDefinition = "mediumtext")
    private String resultJson;

    @Column(nullable = false)
    private Date createdAt = new Date();

    private Date completedAt;

    public void running() {
        this.status = LogAnalysisTaskStatus.RUNNING;
    }

    public void succeed(String resultJson) {
        this.status = LogAnalysisTaskStatus.SUCCEEDED;
        this.resultJson = resultJson;
        this.completedAt = new Date();
    }

    public void fail(String resultJson) {
        this.status = LogAnalysisTaskStatus.FAILED;
        this.resultJson = resultJson;
        this.completedAt = new Date();
    }
}
