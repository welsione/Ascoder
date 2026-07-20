package cn.welsione.ascoder.analysis;

import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.SafePathValidator;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文本搜索工具集，纯 Java 实现（{@link Files#walk} + {@link Pattern}）。不依赖 ripgrep 等外部二进制，跨平台行为一致。
 * <ul>
 *   <li>{@code text_search}：跨文件正则搜索，返回 file:line:content</li>
 *   <li>{@code text_count}：统计每个文件的正则匹配数</li>
 *   <li>{@code text_grep_lines}：单文件内正则搜索</li>
 * </ul>
 */
@Slf4j
public class TextSearchTools {

    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final int ABSOLUTE_MAX_RESULTS = 200;
    private static final int CONTEXT_LINES = 2;
    private static final int MAX_LINE_LENGTH = 2000;

    private final CodeGraphWorkspaceContext workspaceContext;
    private final CodeGraphToolSupport support;

    public TextSearchTools(CodeGraphWorkspaceContext workspaceContext,
                           AtomicReference<String> codeContext,
                           String metadata) {
        this.workspaceContext = workspaceContext;
        this.support = new CodeGraphToolSupport(codeContext, metadata);
    }

    public void registerAll(Toolkit toolkit) {
        toolkit.registration().tool(this).apply();
    }

    // ===== @Tool 1: text_search =====

    @Tool(
            name = "text_search",
            description = """
                    Regex search across files. Returns up to maxResults matches in the format
                    `filePath:lineNumber:matchedLine`. Use this when CodeGraph symbol search
                    is too narrow (e.g. error messages, log strings, comments).
                    """
    )
    public Mono<ToolResultBlock> searchText(
            @ToolParam(name = "pattern", required = true, description = "Java regex pattern.") String pattern,
            @ToolParam(name = "directory", required = false, description = "Directory to search. Default is repository root.") String directory,
            @ToolParam(name = "includeGlob", required = false, description = "Glob filter, e.g. **/*.java.") String includeGlob,
            @ToolParam(name = "maxResults", required = false, description = "Max results. Default is 50, capped at 200.") Integer maxResults,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            Pattern compiled = compilePattern(pattern);
            if (compiled == null) {
                return ToolResultBlock.error("Invalid regex pattern: " + pattern);
            }
            int cap = clampMax(maxResults);
            String dir = directory == null || directory.isBlank() ? "." : directory;
            if (dir.contains("..")) {
                return ToolResultBlock.error("text_search: directory must not contain '..'");
            }
            Path repoRoot = workspaceContext.resolveGitPath(repositoryName);
            Path scanRoot;
            try {
                scanRoot = SafePathValidator.validateUnderRoot(repoRoot, dir);
            } catch (IllegalArgumentException ex) {
                return ToolResultBlock.error(ex.getMessage());
            }
            String dedupeKey = pattern + "|" + dir + "|" + (includeGlob == null ? "" : includeGlob) + "|" + cap;
            ToolResultBlock dup = support.checkDuplicate("text_search", dedupeKey);
            if (dup != null) {
                return dup;
            }
            List<String> lines = new ArrayList<>();
            int truncatedFiles = 0;
            try (Stream<Path> walk = Files.walk(scanRoot, 5)) {
                var iter = walk.filter(Files::isRegularFile)
                        .filter(p -> includeGlob == null || includeGlob.isBlank() || matchesGlob(p, scanRoot, includeGlob))
                        .iterator();
                while (iter.hasNext() && lines.size() < cap) {
                    Path file = iter.next();
                    searchFile(file, scanRoot, compiled, lines, cap, false);
                }
                // 统计截断的文件数
                while (iter.hasNext()) {
                    iter.next();
                    truncatedFiles++;
                }
            } catch (IOException ex) {
                return ToolResultBlock.error("Directory scan failed: " + ex.getMessage());
            }
            String body = formatSearchResults(lines, cap, truncatedFiles, pattern, dir);
            return support.toToolResult("text_search", dedupeKey, new CodeGraphToolResult(true, body));
        });
    }

    // ===== @Tool 2: text_count =====

    @Tool(
            name = "text_count",
            description = """
                    Count regex matches per file. Output: `filePath: count` per line.
            Use this for quick aggregation before deeper search.
                    """
    )
    public Mono<ToolResultBlock> countText(
            @ToolParam(name = "pattern", required = true, description = "Java regex pattern.") String pattern,
            @ToolParam(name = "directory", required = false, description = "Directory to search. Default is repository root.") String directory,
            @ToolParam(name = "includeGlob", required = false, description = "Glob filter, e.g. **/*.java.") String includeGlob,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            Pattern compiled = compilePattern(pattern);
            if (compiled == null) {
                return ToolResultBlock.error("Invalid regex pattern: " + pattern);
            }
            String dir = directory == null || directory.isBlank() ? "." : directory;
            if (dir.contains("..")) {
                return ToolResultBlock.error("text_count: directory must not contain '..'");
            }
            Path repoRoot = workspaceContext.resolveGitPath(repositoryName);
            Path scanRoot;
            try {
                scanRoot = SafePathValidator.validateUnderRoot(repoRoot, dir);
            } catch (IllegalArgumentException ex) {
                return ToolResultBlock.error(ex.getMessage());
            }
            String dedupeKey = pattern + "|" + dir + "|" + (includeGlob == null ? "" : includeGlob);
            ToolResultBlock dup = support.checkDuplicate("text_count", dedupeKey);
            if (dup != null) {
                return dup;
            }
            List<String> counts = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(scanRoot, 5)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> includeGlob == null || includeGlob.isBlank() || matchesGlob(p, scanRoot, includeGlob))
                        .forEach(file -> {
                            int n = countMatches(file, compiled);
                            if (n > 0) {
                                counts.add(scanRoot.relativize(file) + ": " + n);
                            }
                        });
            } catch (IOException ex) {
                return ToolResultBlock.error("Directory scan failed: " + ex.getMessage());
            }
            String body = counts.isEmpty()
                    ? "No files matched pattern '%s' in directory '%s'. Try a different pattern or directory.".formatted(pattern, dir)
                    : "Found " + counts.size() + " files with matches:\n" + String.join("\n", counts);
            return support.toToolResult("text_count", dedupeKey, new CodeGraphToolResult(true, body));
        });
    }

    // ===== @Tool 3: text_grep_lines =====

    @Tool(
            name = "text_grep_lines",
            description = """
                    Search within a specific file (faster than text_search when you know the file).
                    Returns matching line numbers and content.
                    """
    )
    public Mono<ToolResultBlock> grepLines(
            @ToolParam(name = "pattern", required = true, description = "Java regex pattern.") String pattern,
            @ToolParam(name = "filePath", required = true, description = "File path relative to repository root.") String filePath,
            @ToolParam(name = "maxResults", required = false, description = "Max results. Default is 50, capped at 200.") Integer maxResults,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            Pattern compiled = compilePattern(pattern);
            if (compiled == null) {
                return ToolResultBlock.error("Invalid regex pattern: " + pattern);
            }
            if (filePath == null || filePath.isBlank() || filePath.contains("..")) {
                return ToolResultBlock.error("text_grep_lines: filePath must be non-empty and must not contain '..'");
            }
            int cap = clampMax(maxResults);
            Path repoRoot = workspaceContext.resolveGitPath(repositoryName);
            Path resolved;
            try {
                resolved = SafePathValidator.validateUnderRoot(repoRoot, filePath);
            } catch (IllegalArgumentException ex) {
                return ToolResultBlock.error(ex.getMessage());
            }
            if (!Files.isRegularFile(resolved)) {
                return ToolResultBlock.error("Not a regular file or does not exist: " + filePath);
            }
            String dedupeKey = pattern + "|" + filePath + "|" + cap;
            ToolResultBlock dup = support.checkDuplicate("text_grep_lines", dedupeKey);
            if (dup != null) {
                return dup;
            }
            List<String> lines = new ArrayList<>();
            searchFile(resolved, repoRoot, compiled, lines, cap, true);
            String body = lines.isEmpty()
                    ? "No matches for pattern '%s' in file '%s'. Try a different pattern or check the file path.".formatted(pattern, filePath)
                    : "Found " + lines.size() + " matching lines in " + filePath + ":\n" + String.join("\n", lines);
            return support.toToolResult("text_grep_lines", dedupeKey,
                    new CodeGraphToolResult(true, body));
        });
    }

    // ===== 私有辅助 =====

    private Pattern compilePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            return null;
        }
    }

    private int clampMax(Integer maxResults) {
        if (maxResults == null || maxResults <= 0) {
            return DEFAULT_MAX_RESULTS;
        }
        return Math.min(maxResults, ABSOLUTE_MAX_RESULTS);
    }

    private boolean matchesGlob(Path file, Path base, String glob) {
        // Java PathMatcher 对 `**/*.x` 匹配单层文件不稳定（JDK 行为不一致）。
        // 对简单后缀 glob 形如 `**/*.ext` 用 endsWith 替代；其他 glob 走 PathMatcher
        if (glob.startsWith("**/") && !glob.substring(3).contains("/")) {
            // 例如 `**/*.java` → 后缀 `.java`
            String filename = glob.substring(3);
            int firstStar = filename.indexOf('*');
            if (firstStar >= 0) {
                filename = filename.substring(firstStar + 1);  // 取 *.java → .java
            }
            return file.getFileName().toString().endsWith(filename);
        }
        String matcher = "glob:" + glob;
        try {
            return base.getFileSystem().getPathMatcher(matcher).matches(base.relativize(file));
        } catch (Exception ex) {
            return false;
        }
    }

    private int countMatches(Path file, Pattern pattern) {
        try (Stream<String> lines = Files.lines(file)) {
            return (int) lines.filter(l -> pattern.matcher(l).find()).count();
        } catch (IOException ex) {
            return 0;
        }
    }

    /**
     * 在单个文件中搜索，结果追加到 lines。返回剩余配额（外部可继续累积）。
     */
    private void searchFile(Path file, Path base, Pattern pattern, List<String> sink, int cap, boolean alwaysShowContext) {
        try {
            // 二进制文件快速跳过
            if (isBinaryFile(file)) {
                return;
            }
            List<String> lines = Files.readAllLines(file);
            String relPath = base.relativize(file).toString();
            for (int i = 0; i < lines.size(); i++) {
                if (sink.size() >= cap) {
                    return;
                }
                String line = lines.get(i);
                if (line.length() > MAX_LINE_LENGTH) {
                    line = line.substring(0, MAX_LINE_LENGTH) + " ... (line truncated)";
                }
                if (pattern.matcher(line).find()) {
                    sink.add(formatMatch(relPath, i + 1, line, lines, alwaysShowContext));
                }
            }
        } catch (IOException ex) {
            log.debug("搜索文件失败已跳过：path={}，错误={}", file, ex.getMessage());
        }
    }

    private String formatMatch(String relPath, int lineNo, String matchedLine, List<String> allLines, boolean alwaysShowContext) {
        if (!alwaysShowContext) {
            return relPath + ":" + lineNo + ": " + matchedLine;
        }
        // text_grep_lines 模式：附前后 CONTEXT_LINES 行
        StringBuilder out = new StringBuilder();
        out.append(relPath).append(":").append(lineNo).append(": ").append(matchedLine);
        int from = Math.max(0, lineNo - 1 - CONTEXT_LINES);
        int to = Math.min(allLines.size(), lineNo - 1 + CONTEXT_LINES + 1);
        for (int i = from; i < to; i++) {
            if (i == lineNo - 1) {
                continue;  // 主匹配行已显示
            }
            String ctx = allLines.get(i);
            if (ctx.length() > MAX_LINE_LENGTH) {
                ctx = ctx.substring(0, MAX_LINE_LENGTH) + " ...";
            }
            out.append("\n  ").append(i + 1).append(": ").append(ctx);
        }
        return out.toString();
    }

    private String formatSearchResults(List<String> lines, int cap, int truncatedFiles, String pattern, String dir) {
        if (lines.isEmpty()) {
            return "No matches found for pattern '%s' in directory '%s'. Try a different pattern or directory.".formatted(pattern, dir);
        }
        StringBuilder out = new StringBuilder();
        out.append("Found ").append(lines.size()).append(" matches");
        if (lines.size() >= cap) {
            out.append(" (capped at " + cap + ")");
        }
        if (truncatedFiles > 0) {
            out.append(", ").append(truncatedFiles).append(" more files skipped");
        }
        out.append(":\n");
        out.append(lines.stream().collect(Collectors.joining("\n")));
        return out.toString();
    }

    private boolean isBinaryFile(Path file) {
        try {
            // 简单启发式：前 8KB 中 null 字节占比 > 5% 视为二进制
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[Math.min((int) Math.min(file.toFile().length(), 8192), 8192)];
                int read = in.read(buffer);
                if (read <= 0) {
                    return false;
                }
                int nulls = 0;
                for (int i = 0; i < read; i++) {
                    if (buffer[i] == 0) {
                        nulls++;
                    }
                }
                return nulls * 20 > read;  // > 5% null bytes
            }
        } catch (IOException ex) {
            return true;  // 不可读 = 视为二进制跳过
        }
    }
}
