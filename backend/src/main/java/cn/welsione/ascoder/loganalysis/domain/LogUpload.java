package cn.welsione.ascoder.loganalysis.domain;

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
 * 日志上传聚合根，记录一次原始上传事件及其预处理摘要。
 */
@Entity
@Table(name = "logUploads")
@Getter
@Setter
@NoArgsConstructor
public class LogUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    private ProjectSpace projectSpace;

    @Column(length = 128)
    private String createdBy;

    @Column(nullable = false, length = 512)
    private String originalFilename;

    @Column(nullable = false, length = 1024)
    private String storedPath;

    @Column(nullable = false, length = 32)
    private String fileType;

    @Column(nullable = false)
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LogUploadStatus status = LogUploadStatus.UPLOADING;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(columnDefinition = "mediumtext")
    private String summaryJson;

    @Column(nullable = false)
    private Date createdAt = new Date();

    private Date expiresAt;

    public void parsing() {
        this.status = LogUploadStatus.PARSING;
    }

    public void ready(String summaryJson) {
        this.status = LogUploadStatus.READY;
        this.summaryJson = summaryJson;
        this.errorMessage = null;
    }

    public void fail(String message) {
        this.status = LogUploadStatus.FAILED;
        this.errorMessage = message;
    }
}
