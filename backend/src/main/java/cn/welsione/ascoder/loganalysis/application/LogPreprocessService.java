package cn.welsione.ascoder.loganalysis.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志预处理器，按流式方式读取日志，提取 ERROR/WARN/异常栈/traceId/时间戳，
 * 按 fingerprint 聚合异常并生成可被 Agent 工具消费的摘要。
 *
 * <p>所有读取均使用 BufferedReader 按行流式扫描，并在累计字节数超过阈值时进入受限模式
 * （仅保留前 N MB 与命中窗口），避免一次性把大日志读入内存。</p>
 */
@Slf4j
@Service
public class LogPreprocessService {

    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\b(ERROR|WARN|WARNING|INFO|DEBUG|TRACE)\\b");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,3})?)");
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile(
            "\\b(?:traceId|trace_id|tid|requestId|request_id)\\s*[=:]\\s*([A-Za-z0-9._-]{6,})");
    private static final Pattern STACK_AT_PATTERN = Pattern.compile("^\\s*at\\s+([\\w$.]+)\\.([\\w$<>]+)\\(([^)]*)\\)");
    private static final Pattern EXCEPTION_HEADER_PATTERN = Pattern.compile(
            "(?:^|\\s)((?:[\\w$.]+\\.)?[A-Z][\\w$]*(?:Exception|Error|Throwable))(?::\\s*(.{0,500}))?");
    private static final Pattern HTTP_PATH_PATTERN = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\s+(/[\\w/.\\-_]+)");

    private final String[] timestampFormats = new String[] {
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss,SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
    };

    @Value("${ascoder.log-analysis.max-scan-bytes:52428800}")
    private long maxScanBytes;

    @Value("${ascoder.log-analysis.max-keyword-hits:200}")
    private int maxKeywordHits;

    @Value("${ascoder.log-analysis.max-trace-ids:50}")
    private int maxTraceIds;

    public LogFileSummary preprocess(Long fileId, String displayName, Path file) {
        log.info("开始预处理日志文件，fileId={}，path={}", fileId, file);
        LogFileSummary summary = new LogFileSummary();
        summary.setFileId(fileId);
        summary.setDisplayName(displayName);
        long fileSize;
        try {
            fileSize = Files.size(file);
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取日志文件大小：" + ex.getMessage(), ex);
        }
        summary.setFileSize(fileSize);

        Map<String, ExceptionAggregator> exceptions = new LinkedHashMap<>();
        List<LogFileSummary.KeywordHit> keywordHits = new ArrayList<>();
        Map<String, Boolean> traceSeen = new LinkedHashMap<>();
        long bytesRead = 0;
        long lineCount = 0;
        Date earliest = null;
        Date latest = null;
        ExceptionAggregator currentException = null;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                bytesRead += line.length() + 1L;
                if (bytesRead > maxScanBytes) {
                    summary.setLimitedMode(true);
                    log.warn("日志文件超过扫描阈值，进入受限模式，fileId={}，bytesRead={}", fileId, bytesRead);
                    break;
                }

                LogEntry entry = parseLine(line, (int) lineCount, bytesRead);
                accumulateLevel(summary, entry.getLevel());
                if (entry.getTimestamp() != null) {
                    if (earliest == null || entry.getTimestamp().before(earliest)) {
                        earliest = entry.getTimestamp();
                    }
                    if (latest == null || entry.getTimestamp().after(latest)) {
                        latest = entry.getTimestamp();
                    }
                }
                if (entry.getTraceId() != null && traceSeen.size() < maxTraceIds) {
                    traceSeen.put(entry.getTraceId(), Boolean.TRUE);
                }

                currentException = handleStackFrames(line, currentException);
                Matcher headerMatcher = EXCEPTION_HEADER_PATTERN.matcher(line);
                if (headerMatcher.find() && headerMatcher.group(1).length() > 5) {
                    currentException = openException(exceptions, headerMatcher, line, entry);
                } else if (currentException != null && line.isBlank()) {
                    currentException = null;
                }

                if (keywordHits.size() < maxKeywordHits) {
                    collectKeywordHits(line, entry, keywordHits);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取日志失败：" + ex.getMessage(), ex);
        }

        summary.setLineCount(lineCount);
        summary.setScannedBytes(bytesRead);
        summary.setStartedAt(earliest);
        summary.setEndedAt(latest);
        summary.setTraceIds(new ArrayList<>(traceSeen.keySet()));
        summary.setKeywordHits(keywordHits);
        summary.setExceptionGroups(exceptions.values().stream()
                .sorted((a, b) -> Long.compare(b.count, a.count))
                .map(ExceptionAggregator::toGroup)
                .toList());
        log.info("预处理完成，fileId={}，lineCount={}，errorCount={}，exceptionGroups={}",
                fileId, lineCount, summary.getErrorCount(), summary.getExceptionGroups().size());
        return summary;
    }

    private LogEntry parseLine(String line, int lineNo, long byteOffset) {
        LogEntry entry = new LogEntry();
        entry.setLineNo(lineNo);
        entry.setByteOffset(byteOffset);
        entry.setRawLine(line);

        Matcher levelMatcher = LEVEL_PATTERN.matcher(line);
        if (levelMatcher.find()) {
            String raw = levelMatcher.group(1);
            entry.setLevel("WARNING".equals(raw) ? "WARN" : raw);
        }
        Matcher tsMatcher = TIMESTAMP_PATTERN.matcher(line);
        if (tsMatcher.find()) {
            entry.setTimestamp(parseTimestamp(tsMatcher.group(1)));
        }
        Matcher traceMatcher = TRACE_ID_PATTERN.matcher(line);
        if (traceMatcher.find()) {
            entry.setTraceId(traceMatcher.group(1));
        }
        return entry;
    }

    private Date parseTimestamp(String raw) {
        String normalized = raw.replace('T', ' ').replace(',', '.');
        for (String pattern : timestampFormats) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(pattern);
                return fmt.parse(normalized);
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    private void accumulateLevel(LogFileSummary summary, String level) {
        if (level == null) {
            return;
        }
        switch (level) {
            case "ERROR" -> summary.setErrorCount(summary.getErrorCount() + 1);
            case "WARN" -> summary.setWarnCount(summary.getWarnCount() + 1);
            case "INFO" -> summary.setInfoCount(summary.getInfoCount() + 1);
            case "DEBUG", "TRACE" -> summary.setDebugCount(summary.getDebugCount() + 1);
            default -> {
                // ignored
            }
        }
    }

    private ExceptionAggregator handleStackFrames(String line, ExceptionAggregator current) {
        if (current == null) {
            return null;
        }
        Matcher atMatcher = STACK_AT_PATTERN.matcher(line);
        if (!atMatcher.find()) {
            return current;
        }
        String className = atMatcher.group(1);
        String method = atMatcher.group(2);
        if (current.topFrames.size() < 5) {
            current.topFrames.add(className + "." + method);
        }
        if (current.snippet.length() < 1500) {
            current.snippet.append(line).append('\n');
            current.lineEnd++;
        }
        return current;
    }

    private ExceptionAggregator openException(Map<String, ExceptionAggregator> bucket, Matcher matcher,
                                              String line, LogEntry entry) {
        String exceptionClass = matcher.group(1);
        String message = matcher.group(2) == null ? "" : matcher.group(2).trim();
        String normalized = normalizeMessage(message);
        String fingerprint = exceptionClass + "|" + normalized;
        ExceptionAggregator agg = bucket.computeIfAbsent(fingerprint, k -> {
            ExceptionAggregator created = new ExceptionAggregator();
            created.fingerprint = fingerprint;
            created.exceptionClass = exceptionClass;
            created.normalizedMessage = normalized;
            created.lineStart = entry.getLineNo();
            created.lineEnd = entry.getLineNo();
            created.snippet.append(line).append('\n');
            return created;
        });
        agg.count++;
        if (agg.firstSeenAt == null || (entry.getTimestamp() != null && entry.getTimestamp().before(agg.firstSeenAt))) {
            agg.firstSeenAt = entry.getTimestamp();
        }
        if (agg.lastSeenAt == null || (entry.getTimestamp() != null && entry.getTimestamp().after(agg.lastSeenAt))) {
            agg.lastSeenAt = entry.getTimestamp();
        }
        if (entry.getTraceId() != null && !agg.traceIds.contains(entry.getTraceId()) && agg.traceIds.size() < 10) {
            agg.traceIds.add(entry.getTraceId());
        }
        return agg;
    }

    private void collectKeywordHits(String line, LogEntry entry, List<LogFileSummary.KeywordHit> hits) {
        Matcher pathMatcher = HTTP_PATH_PATTERN.matcher(line);
        if (pathMatcher.find()) {
            hits.add(new LogFileSummary.KeywordHit("httpPath",
                    pathMatcher.group(1) + " " + pathMatcher.group(2),
                    entry.getLineNo(),
                    entry.getTimestamp()));
            return;
        }
        if (entry.getTraceId() != null) {
            hits.add(new LogFileSummary.KeywordHit("traceId", entry.getTraceId(),
                    entry.getLineNo(), entry.getTimestamp()));
        }
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String stripped = message.replaceAll("\\d+", "?")
                .replaceAll("\"[^\"]*\"", "\"?\"")
                .replaceAll("'[^']*'", "'?'");
        return stripped.length() > 200 ? stripped.substring(0, 200) : stripped;
    }

    private static class ExceptionAggregator {
        String fingerprint;
        String exceptionClass;
        String normalizedMessage;
        long count;
        Date firstSeenAt;
        Date lastSeenAt;
        final List<String> topFrames = new ArrayList<>();
        final List<String> traceIds = new ArrayList<>();
        final StringBuilder snippet = new StringBuilder();
        int lineStart;
        int lineEnd;

        LogFileSummary.ExceptionGroup toGroup() {
            return new LogFileSummary.ExceptionGroup(
                    fingerprint,
                    exceptionClass,
                    normalizedMessage,
                    count,
                    firstSeenAt,
                    lastSeenAt,
                    new ArrayList<>(topFrames),
                    snippet.toString().strip(),
                    lineStart,
                    lineEnd,
                    new ArrayList<>(traceIds)
            );
        }
    }

    Map<String, Long> emptyLevels() {
        return new HashMap<>();
    }
}
