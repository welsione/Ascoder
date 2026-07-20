package cn.welsione.ascoder.loganalysis.domain;

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
 * 日志文件实体，记录上传后单个被解析的日志。
 */
@Entity
@Table(name = "logFiles")
@Getter
@Setter
@NoArgsConstructor
public class LogFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploadId", nullable = false)
    private LogUpload upload;

    @Column(nullable = false, length = 512)
    private String displayName;

    @Column(nullable = false, length = 1024)
    private String storedPath;

    @Column(nullable = false)
    private Long fileSize;

    private Long lineCount;

    private Date startedAt;

    private Date endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LogFileParseStatus parseStatus = LogFileParseStatus.PENDING;

    @Column(nullable = false)
    private boolean limitedMode;

    @Column(columnDefinition = "mediumtext")
    private String summaryJson;

    @Column(nullable = false)
    private Date createdAt = new Date();
}
