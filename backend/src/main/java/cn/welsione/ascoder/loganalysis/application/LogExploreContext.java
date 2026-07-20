package cn.welsione.ascoder.loganalysis.application;

import cn.welsione.ascoder.loganalysis.domain.LogFile;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import lombok.Getter;

import java.util.List;

/**
 * 日志探索工具运行上下文，封装一次问答中可访问的日志上传与文件集合，
 * 由 LogExploreToolService 在装配工具集时一次性构造。
 */
@Getter
public class LogExploreContext {

    private final LogUpload upload;
    private final List<LogFile> files;
    private final String summaryJson;

    public LogExploreContext(LogUpload upload, List<LogFile> files) {
        this.upload = upload;
        this.files = files;
        this.summaryJson = upload == null ? null : upload.getSummaryJson();
    }

    public LogFile findFile(Long fileId) {
        if (fileId == null || files == null) {
            return null;
        }
        return files.stream()
                .filter(f -> fileId.equals(f.getId()))
                .findFirst()
                .orElse(null);
    }

    public LogFile firstFile() {
        return files == null || files.isEmpty() ? null : files.get(0);
    }
}
