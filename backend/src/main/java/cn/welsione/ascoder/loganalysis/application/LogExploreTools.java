package cn.welsione.ascoder.loganalysis.application;

import cn.welsione.ascoder.loganalysis.domain.LogFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 日志探索工具集，向 Agent 暴露 4 个 P0 工具：
 * <ul>
 *   <li>{@code log_summary}：返回上传整体摘要（文件数量、级别计数、时间窗口、TraceId）</li>
 *   <li>{@code log_exception_groups}：列出按 fingerprint 聚合的异常组</li>
 *   <li>{@code log_search}：在日志原文中按关键词/正则定位行号</li>
 *   <li>{@code log_snippet}：读取指定行范围的日志片段（受脱敏服务保护）</li>
 * </ul>
 *
 * <p>所有工具仅读取 LogPreprocessService 已落盘的文件路径，并通过 LogMaskingService 脱敏。</p>
 */
@Slf4j
public class LogExploreTools {

    private static final TypeReference<List<LogFileSummary>> FILE_SUMMARY_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private static final int MAX_SNIPPET_LINES = 200;
    private static final int MAX_SEARCH_HITS = 50;
    private static final int MAX_LINE_LENGTH = 1000;

    private final LogExploreContext context;
    private final ObjectMapper objectMapper;
    private final LogMaskingService maskingService;

    public LogExploreTools(LogExploreContext context, ObjectMapper objectMapper, LogMaskingService maskingService) {
        this.context = context;
        this.objectMapper = objectMapper;
        this.maskingService = maskingService;
    }

    public void registerAll(Toolkit toolkit) {
        toolkit.registration().tool(this).apply();
        log.debug("LogExploreTools 注册完成，共 4 个 @Tool 方法");
    }

    @Tool(
            name = "log_summary",
            description = """
                    Return the high level summary of the uploaded log bundle: file count, total size,
                    error/warn/info counts, time window, top trace ids, and a list of files (id, name, lineCount).
                    Call this FIRST before deeper exploration.
                    """
    )
    public Mono<ToolResultBlock> summary() {
        return Mono.fromCallable(() -> {
            if (context.getUpload() == null) {
                return ToolResultBlock.error("没有可用的日志上传上下文");
            }
            String summaryJson = context.getSummaryJson();
            if (summaryJson == null || summaryJson.isBlank()) {
                return ToolResultBlock.text("当前日志暂无摘要数据");
            }
            try {
                Map<String, Object> root = objectMapper.readValue(summaryJson, JSON_MAP);
                StringBuilder out = new StringBuilder();
                out.append("# Log Upload Summary\n")
                        .append("uploadId=").append(root.getOrDefault("uploadId", "?")).append('\n')
                        .append("totalBytes=").append(root.getOrDefault("totalFileSize", 0)).append('\n')
                        .append("limitedMode=").append(root.getOrDefault("limitedMode", false)).append('\n');
                Object filesObj = root.get("files");
                if (filesObj != null) {
                    String filesJson = objectMapper.writeValueAsString(filesObj);
                    List<LogFileSummary> files = objectMapper.readValue(filesJson, FILE_SUMMARY_LIST);
                    out.append("\n## Files (").append(files.size()).append(")\n");
                    for (LogFileSummary f : files) {
                        out.append("- fileId=").append(f.getFileId())
                                .append(", name=").append(f.getDisplayName())
                                .append(", lines=").append(f.getLineCount())
                                .append(", error=").append(f.getErrorCount())
                                .append(", warn=").append(f.getWarnCount())
                                .append(", limited=").append(f.isLimitedMode())
                                .append(", timeRange=").append(formatRange(f.getStartedAt(), f.getEndedAt()))
                                .append('\n');
                        if (f.getTraceIds() != null && !f.getTraceIds().isEmpty()) {
                            out.append("  traceIds=").append(joinTop(f.getTraceIds(), 5)).append('\n');
                        }
                    }
                }
                return ToolResultBlock.text(out.toString());
            } catch (IOException ex) {
                return ToolResultBlock.error("解析日志摘要失败：" + ex.getMessage());
            }
        });
    }

    @Tool(
            name = "log_exception_groups",
            description = """
                    List exception groups aggregated by fingerprint. Each group contains:
                    fingerprint, exceptionClass, normalizedMessage, count, first/last seen,
                    representative line range, top frames, and related trace ids.
                    Use this to identify the dominant failure pattern.
                    """
    )
    public Mono<ToolResultBlock> exceptionGroups(
            @ToolParam(name = "fileId", required = false, description = "Optional fileId to scope the query. Omit to query all files.") Long fileId,
            @ToolParam(name = "limit", required = false, description = "Max groups to return. Default is 10, capped at 30.") Integer limit
    ) {
        return Mono.fromCallable(() -> {
            int cap = limit == null || limit <= 0 ? 10 : Math.min(limit, 30);
            List<LogFileSummary> summaries = loadFileSummaries(fileId);
            if (summaries.isEmpty()) {
                return ToolResultBlock.error("没有可用的日志上传上下文");
            }
            StringBuilder out = new StringBuilder("# Exception Groups\n");
            int total = 0;
            for (LogFileSummary fs : summaries) {
                if (fs.getExceptionGroups() == null || fs.getExceptionGroups().isEmpty()) {
                    continue;
                }
                out.append("\n## File: ").append(fs.getDisplayName())
                        .append(" (fileId=").append(fs.getFileId()).append(")\n");
                List<LogFileSummary.ExceptionGroup> groups = fs.getExceptionGroups();
                int show = Math.min(groups.size(), cap);
                for (int i = 0; i < show; i++) {
                    LogFileSummary.ExceptionGroup g = groups.get(i);
                    out.append("- [").append(i + 1).append("] ")
                            .append(g.getExceptionClass())
                            .append(" x").append(g.getCount())
                            .append(", lines=").append(g.getRepresentativeLineStart())
                            .append('-').append(g.getRepresentativeLineEnd())
                            .append(", time=").append(formatRange(g.getFirstSeenAt(), g.getLastSeenAt()))
                            .append('\n');
                    if (g.getNormalizedMessage() != null && !g.getNormalizedMessage().isBlank()) {
                        out.append("  msg=").append(maskingService.mask(g.getNormalizedMessage())).append('\n');
                    }
                    if (g.getTopApplicationFrames() != null && !g.getTopApplicationFrames().isEmpty()) {
                        out.append("  topFrames=").append(joinTop(g.getTopApplicationFrames(), 3)).append('\n');
                    }
                    if (g.getRelatedTraceIds() != null && !g.getRelatedTraceIds().isEmpty()) {
                        out.append("  traceIds=").append(joinTop(g.getRelatedTraceIds(), 5)).append('\n');
                    }
                }
                total += show;
            }
            if (total == 0) {
                return ToolResultBlock.text("No exception groups found in uploaded logs. All log entries appear to be normal.");
            }
            return ToolResultBlock.text(out.toString());
        });
    }

    @Tool(
            name = "log_search",
            description = """
                    Search the log file content by keyword or regex and return up to 50 matching lines
                    with their line numbers. Use this to locate trace ids, http paths, or specific tokens.
                    Output is masked for sensitive data.
                    """
    )
    public Mono<ToolResultBlock> search(
            @ToolParam(name = "keyword", required = true, description = "Keyword or regex to search.") String keyword,
            @ToolParam(name = "regex", required = false, description = "Whether to interpret keyword as a regex. Default false.") Boolean regex,
            @ToolParam(name = "fileId", required = false, description = "Optional fileId to scope the search.") Long fileId,
            @ToolParam(name = "maxHits", required = false, description = "Max hits to return. Default 30, capped at 50.") Integer maxHits
    ) {
        return Mono.fromCallable(() -> {
            if (keyword == null || keyword.isBlank()) {
                return ToolResultBlock.error("keyword 不能为空");
            }
            int cap = maxHits == null || maxHits <= 0 ? 30 : Math.min(maxHits, MAX_SEARCH_HITS);
            boolean useRegex = Boolean.TRUE.equals(regex);
            Pattern pattern;
            try {
                pattern = useRegex
                        ? Pattern.compile(keyword)
                        : Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);
            } catch (Exception ex) {
                return ToolResultBlock.error("正则编译失败：" + ex.getMessage());
            }
            List<LogFile> targets = filterFiles(fileId);
            if (targets.isEmpty()) {
                return ToolResultBlock.text("没有可搜索的日志文件");
            }
            StringBuilder out = new StringBuilder("# Log Search\nkeyword=" + keyword + ", regex=" + useRegex + "\n");
            int hitCount = 0;
            for (LogFile f : targets) {
                if (hitCount >= cap) {
                    break;
                }
                Path path = Paths.get(f.getStoredPath());
                if (!Files.exists(path)) {
                    continue;
                }
                out.append("\n## File: ").append(f.getDisplayName())
                        .append(" (fileId=").append(f.getId()).append(")\n");
                try {
                    int lineNo = 0;
                    var iter = Files.lines(path, StandardCharsets.UTF_8).iterator();
                    while (iter.hasNext() && hitCount < cap) {
                        lineNo++;
                        String line = iter.next();
                        if (pattern.matcher(line).find()) {
                            String masked = maskingService.mask(truncate(line));
                            out.append(String.format("L%6d: %s%n", lineNo, masked));
                            hitCount++;
                        }
                    }
                } catch (IOException ex) {
                    out.append("(读取文件失败：").append(ex.getMessage()).append(")\n");
                }
            }
            if (hitCount == 0) {
                return ToolResultBlock.text("未匹配到任何日志行");
            }
            return ToolResultBlock.text(out.toString());
        });
    }

    @Tool(
            name = "log_snippet",
            description = """
                    Read a specific line range from a log file. Returns up to 200 lines with masking applied.
                    Use this after log_exception_groups or log_search to inspect concrete context.
                    """
    )
    public Mono<ToolResultBlock> snippet(
            @ToolParam(name = "fileId", required = false, description = "fileId of the target log file. Omit to use the first file in the upload.") Long fileId,
            @ToolParam(name = "lineStart", required = true, description = "Inclusive starting line number (1-based).") Integer lineStart,
            @ToolParam(name = "lineEnd", required = true, description = "Inclusive ending line number.") Integer lineEnd
    ) {
        return Mono.fromCallable(() -> {
            if (lineStart == null || lineEnd == null || lineStart < 1 || lineEnd < lineStart) {
                return ToolResultBlock.error("行号范围非法，要求 1<=lineStart<=lineEnd");
            }
            int span = lineEnd - lineStart + 1;
            if (span > MAX_SNIPPET_LINES) {
                return ToolResultBlock.error("片段最多 " + MAX_SNIPPET_LINES + " 行，当前请求 " + span);
            }
            LogFile target = fileId == null ? context.firstFile() : context.findFile(fileId);
            if (target == null) {
                return ToolResultBlock.error("找不到目标日志文件 fileId=" + fileId);
            }
            Path path = Paths.get(target.getStoredPath());
            if (!Files.exists(path)) {
                return ToolResultBlock.error("日志文件已不可访问：" + target.getDisplayName());
            }
            StringBuilder out = new StringBuilder("# Log Snippet\nfile=").append(target.getDisplayName())
                    .append(", lines=").append(lineStart).append('-').append(lineEnd).append('\n');
            try {
                int currentLine = 0;
                var iter = Files.lines(path, StandardCharsets.UTF_8).iterator();
                while (iter.hasNext()) {
                    currentLine++;
                    String line = iter.next();
                    if (currentLine < lineStart) {
                        continue;
                    }
                    if (currentLine > lineEnd) {
                        break;
                    }
                    out.append(String.format("L%6d: %s%n", currentLine, maskingService.mask(truncate(line))));
                }
            } catch (IOException ex) {
                return ToolResultBlock.error("读取片段失败：" + ex.getMessage());
            } catch (UncheckedIOException ex) {
                return ToolResultBlock.error("读取片段失败：" + ex.getMessage());
            }
            return ToolResultBlock.text(out.toString());
        });
    }

    private List<LogFileSummary> loadFileSummaries(Long fileId) {
        List<LogFile> targets = filterFiles(fileId);
        List<LogFileSummary> result = new ArrayList<>();
        for (LogFile f : targets) {
            if (f.getSummaryJson() == null || f.getSummaryJson().isBlank()) {
                continue;
            }
            try {
                LogFileSummary s = objectMapper.readValue(f.getSummaryJson(), LogFileSummary.class);
                if (s.getFileId() == null) {
                    s.setFileId(f.getId());
                }
                if (s.getDisplayName() == null) {
                    s.setDisplayName(f.getDisplayName());
                }
                result.add(s);
            } catch (IOException ex) {
                log.warn("反序列化 LogFile 摘要失败 fileId={}", f.getId(), ex);
            }
        }
        return result;
    }

    private List<LogFile> filterFiles(Long fileId) {
        if (context.getFiles() == null) {
            return List.of();
        }
        if (fileId == null) {
            return context.getFiles();
        }
        LogFile match = context.findFile(fileId);
        return match == null ? List.of() : List.of(match);
    }

    private String formatRange(Date start, Date end) {
        if (start == null && end == null) {
            return "(unknown)";
        }
        return (start == null ? "?" : start) + " ~ " + (end == null ? "?" : end);
    }

    private String joinTop(List<String> values, int max) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        int show = Math.min(values.size(), max);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < show; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        if (values.size() > show) {
            sb.append(", ... (+" ).append(values.size() - show).append(')');
        }
        return sb.toString();
    }

    private String truncate(String line) {
        if (line == null) {
            return "";
        }
        if (line.length() <= MAX_LINE_LENGTH) {
            return line;
        }
        return line.substring(0, MAX_LINE_LENGTH) + " ...";
    }
}
