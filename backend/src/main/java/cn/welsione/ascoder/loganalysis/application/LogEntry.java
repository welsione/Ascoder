package cn.welsione.ascoder.loganalysis.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 单条日志条目的解析结果，预处理器流式扫描时输出。仅在内存中传递，不持久化。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {
    private int lineNo;
    private long byteOffset;
    private String level;
    private Date timestamp;
    private String traceId;
    private String message;
    private String rawLine;
}
