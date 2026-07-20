package cn.welsione.ascoder.loganalysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 日志证据引用，记录答案中具体引用的日志片段位置与脱敏内容。
 */
@Entity
@Table(name = "logEvidenceRefs")
@Getter
@Setter
@NoArgsConstructor
public class LogEvidenceRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taskId", nullable = false)
    private LogAnalysisTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logFileId", nullable = false)
    private LogFile logFile;

    @Column(nullable = false)
    private Integer lineStart;

    @Column(nullable = false)
    private Integer lineEnd;

    @Column(columnDefinition = "text")
    private String snippet;

    @Column(columnDefinition = "text")
    private String maskedSnippet;

    @Column(length = 32)
    private String evidenceType;

    @Column(nullable = false)
    private Date createdAt = new Date();
}
