package cn.welsione.ascoder.loganalysis.web;

import cn.welsione.ascoder.loganalysis.application.LogFileSummary;
import cn.welsione.ascoder.loganalysis.application.LogUploadSummary;
import cn.welsione.ascoder.loganalysis.domain.LogFile;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 日志上传响应 DTO，对外暴露上传任务状态及简要文件摘要。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogUploadResponse {

    private Long id;
    private Long projectSpaceId;
    private String originalFilename;
    private String fileType;
    private long fileSize;
    private String status;
    private String errorMessage;
    private Date createdAt;
    private Date expiresAt;
    private List<LogFileBrief> files = new ArrayList<>();
    private LogUploadSummary summary;

    public static LogUploadResponse from(LogUpload upload, List<LogFile> files, ObjectMapper objectMapper) {
        LogUploadResponse resp = new LogUploadResponse();
        resp.setId(upload.getId());
        resp.setProjectSpaceId(upload.getProjectSpace() == null ? null : upload.getProjectSpace().getId());
        resp.setOriginalFilename(upload.getOriginalFilename());
        resp.setFileType(upload.getFileType());
        resp.setFileSize(upload.getFileSize() == null ? 0L : upload.getFileSize());
        resp.setStatus(upload.getStatus().name());
        resp.setErrorMessage(upload.getErrorMessage());
        resp.setCreatedAt(upload.getCreatedAt());
        resp.setExpiresAt(upload.getExpiresAt());
        if (files != null) {
            resp.setFiles(files.stream().map(LogFileBrief::from).toList());
        }
        if (upload.getSummaryJson() != null && !upload.getSummaryJson().isBlank()) {
            try {
                resp.setSummary(objectMapper.readValue(upload.getSummaryJson(), LogUploadSummary.class));
            } catch (Exception ignored) {
                // 摘要解析失败不阻塞响应
            }
        }
        return resp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogFileBrief {
        private Long id;
        private String displayName;
        private long fileSize;
        private Long lineCount;
        private boolean limitedMode;
        private String parseStatus;
        private LogFileSummary summary;

        public static LogFileBrief from(LogFile entity) {
            LogFileBrief brief = new LogFileBrief();
            brief.setId(entity.getId());
            brief.setDisplayName(entity.getDisplayName());
            brief.setFileSize(entity.getFileSize() == null ? 0L : entity.getFileSize());
            brief.setLineCount(entity.getLineCount());
            brief.setLimitedMode(entity.isLimitedMode());
            brief.setParseStatus(entity.getParseStatus().name());
            return brief;
        }
    }
}
