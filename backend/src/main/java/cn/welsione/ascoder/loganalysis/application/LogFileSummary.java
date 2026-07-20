package cn.welsione.ascoder.loganalysis.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个日志文件的预处理摘要，作为 Agent 上下文与日志探索工具的索引来源。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogFileSummary {

    private Long fileId;
    private String displayName;
    private long fileSize;
    private long lineCount;
    private long scannedBytes;
    private boolean limitedMode;
    private Date startedAt;
    private Date endedAt;
    private long errorCount;
    private long warnCount;
    private long infoCount;
    private long debugCount;
    private List<String> traceIds = new ArrayList<>();
    private List<ExceptionGroup> exceptionGroups = new ArrayList<>();
    private List<KeywordHit> keywordHits = new ArrayList<>();

    /**
     * 异常聚合，按 fingerprint 归并相同异常。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExceptionGroup {
        private String fingerprint;
        private String exceptionClass;
        private String normalizedMessage;
        private long count;
        private Date firstSeenAt;
        private Date lastSeenAt;
        private List<String> topApplicationFrames = new ArrayList<>();
        private String representativeSnippet;
        private int representativeLineStart;
        private int representativeLineEnd;
        private List<String> relatedTraceIds = new ArrayList<>();
    }

    /**
     * 关键词命中，用于支持 logSearch 工具的近似定位。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordHit {
        private String category;
        private String value;
        private int line;
        private Date timestamp;
    }

    public Map<String, Long> levelCounts() {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("ERROR", errorCount);
        map.put("WARN", warnCount);
        map.put("INFO", infoCount);
        map.put("DEBUG", debugCount);
        return map;
    }
}
