package cn.welsione.ascoder.loganalysis.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志上传整体摘要，包含所有日志文件的概览。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogUploadSummary {

    private Long uploadId;
    private long totalFileSize;
    private boolean limitedMode;
    private List<LogFileSummary> files = new ArrayList<>();
}
