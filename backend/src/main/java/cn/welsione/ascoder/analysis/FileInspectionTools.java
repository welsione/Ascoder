package cn.welsione.ascoder.analysis;

import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.SafePathValidator;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件检查工具集，提供 4 个 @Tool：
 * <ul>
 *   <li>{@code file_read}：读文件内容（支持行范围）</li>
 *   <li>{@code file_list}：列目录（限深度、限数量）</li>
 *   <li>{@code file_info}：文件元数据（大小、修改时间、类型）</li>
 *   <li>{@code file_glob}：glob 模式匹配（找文件）</li>
 * </ul>
 * 全部只读，路径安全由 {@link SafePathValidator} 统一校验。
 */
@Slf4j
public class FileInspectionTools {

    private static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;   // 5MB
    private static final int MAX_LINE_LENGTH = 2000;                  // 单行截断
    private static final int DEFAULT_MAX_ENTRIES = 200;
    private static final int ABSOLUTE_MAX_ENTRIES = 1000;
    private static final int DEFAULT_MAX_DEPTH = 4;
    private static final int ABSOLUTE_MAX_DEPTH = 20;

    private static final String READ_HEADER_PATH = "prompts/templates/file-read-header.md";
    private static final String INFO_BLOCK_PATH = "prompts/templates/file-info-block.md";

    private final CodeGraphWorkspaceContext workspaceContext;
    private final CodeGraphToolSupport support;
    private final PromptManager promptManager;

    public FileInspectionTools(CodeGraphWorkspaceContext workspaceContext,
                               AtomicReference<String> codeContext,
                               String metadata,
                               PromptManager promptManager) {
        this.workspaceContext = workspaceContext;
        this.support = new CodeGraphToolSupport(codeContext, metadata);
        this.promptManager = promptManager;
    }

    public void registerAll(Toolkit toolkit) {
        toolkit.registration().tool(this).apply();
    }

    // ===== @Tool 1: file_read =====

    @Tool(
            name = "file_read",
            description = """
                    Read a file's content with optional line range.
                    Path is relative to the repository root. Hard cap at 5MB.
                    Use this when CodeGraph symbol search is not enough (configs, docs, generated files).
                    """
    )
    public Mono<ToolResultBlock> readFile(
            @ToolParam(name = "path", required = true, description = "File path relative to repository root. Must not contain '..'.") String path,
            @ToolParam(name = "startLine", required = false, description = "Starting line number (1-based, inclusive).") Integer startLine,
            @ToolParam(name = "endLine", required = false, description = "Ending line number (1-based, inclusive).") Integer endLine,
            @ToolParam(name = "maxBytes", required = false, description = "Max bytes to read. Default is 5MB.") Long maxBytes,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            if (path == null || path.isBlank() || path.contains("..")) {
                return ToolResultBlock.error("file_read: path must be non-empty and must not contain '..'");
            }
            Path repoRoot = workspaceContext.resolveGitPath(repositoryName);
            Path resolved;
            try {
                resolved = SafePathValidator.validateUnderRoot(repoRoot, path);
            } catch (IllegalArgumentException ex) {
                return ToolResultBlock.error(ex.getMessage());
            }
            if (!Files.isRegularFile(resolved)) {
                return ToolResultBlock.error("Not a regular file or does not exist: " + path);
            }
            String dedupeKey = path + "|" + (startLine == null ? "" : startLine) + "|" + (endLine == null ? "" : endLine);
            ToolResultBlock dup = support.checkDuplicate("file_read", dedupeKey);
            if (dup != null) {
                return dup;
            }
            long cap = maxBytes == null || maxBytes <= 0
                    ? DEFAULT_MAX_BYTES
                    : Math.min(maxBytes, DEFAULT_MAX_BYTES);
            StringBuilder errorHolder = new StringBuilder();
            Optional<String> contentOpt = readWithLimit(resolved, startLine, endLine, cap, errorHolder);
            if (contentOpt.isEmpty()) {
                return ToolResultBlock.error(errorHolder.toString());
            }
            String content = contentOpt.get();
            FileReadData data = new FileReadData(path, content);
            return support.toToolResult("file_read", dedupeKey,
                    CodeGraphToolResult.success(promptManager.renderFramework(READ_HEADER_PATH, FileReadData.class, data) + "\n" + content));
        });
    }

    // ===== @Tool 2: file_list =====

    @Tool(
            name = "file_list",
            description = """
                    List files and directories under a directory.
                    Path is relative to the repository root. Default depth 2, capped at 5.
                    Returns up to 200 entries by default.
                    """
    )
    public Mono<ToolResultBlock> listFiles(
            @ToolParam(name = "directory", required = false, description = "Directory path. Default is repository root.") String directory,
            @ToolParam(name = "maxDepth", required = false, description = "Maximum depth. Default is 2, capped at 5.") Integer maxDepth,
            @ToolParam(name = "maxEntries", required = false, description = "Maximum entries returned. Default is 200, capped at 1000.") Integer maxEntries,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            String dir = directory == null || directory.isBlank() ? "." : directory;
            if (dir.contains("..")) {
                return ToolResultBlock.error("file_list: directory must not contain '..'");
            }
            int depth = clampDepth(maxDepth);
            int cap = maxEntries == null || maxEntries <= 0
                    ? DEFAULT_MAX_ENTRIES
                    : Math.min(maxEntries, ABSOLUTE_MAX_ENTRIES);
            Path repoRoot = workspaceContext.resolveGitPath(repositoryName);
            Path scanRoot;
            try {
                scanRoot = SafePathValidator.validateUnderRoot(repoRoot, dir);
            } catch (IllegalArgumentException ex) {
                return ToolResultBlock.error(ex.getMessage());
            }
            if (!Files.isDirectory(scanRoot)) {
                return ToolResultBlock.error("Not a directory or does not exist: " + dir);
            }
            String dedupeKey = dir + "|" + depth + "|" + cap;
            ToolResultBlock dup = support.checkDuplicate("file_list", dedupeKey);
            if (dup != null) {
                return dup;
            }
            List<String> lines = new ArrayList<>();
            int truncated = 0;
            try (Stream<Path> walk = Files.walk(scanRoot, depth)) {
                var iter = walk.filter(Files::exists).iterator();
                while (iter.hasNext() && lines.size() < cap) {
                    Path p = iter.next();
                    BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                    String type = attr.isDirectory() ? "dir" : attr.isSymbolicLink() ? "link" : "file";
                    String size = attr.isDirectory() ? "-" : humanReadableSize(attr.size());
                    String rel = scanRoot.relativize(p).toString();
                    if (rel.isEmpty()) {
                        rel = ".";
                    }
                    lines.add(String.format("[%s] %-6s %s", type, size, rel));
                }
                // 统计被截断的条目数
                while (iter.hasNext()) {
                    iter.next();
                    truncated++;
                }
            } catch (IOException ex) {
                return ToolResultBlock.error("Directory scan failed: " + ex.getMessage());
            }
            StringBuilder out = new StringBuilder();
            out.append("Directory: ").append(dir).append(" (depth ").append(depth).append(")\n");
            if (lines.isEmpty()) {
                out.append("(empty directory)");
            } else {
                out.append(String.join("\n", lines));
            }
            if (truncated > 0) {
                out.append("\n... ").append(truncated).append(" more entries truncated (maxEntries=").append(cap).append(")");
            }
            return support.toToolResult("file_list", dedupeKey, new CodeGraphToolResult(true, out.toString()));
        });
    }

    // ===== @Tool 3: file_info =====

    @Tool(
            name = "file_info",
            description = """
                    Get file metadata: size, modified time, type (file/directory/symlink).
                    Use this before file_read to check if a file is too large.
                    """
    )
    public Mono<ToolResultBlock> fileInfo(
            @ToolParam(name = "path", required = true, description = "File path relative to repository root.") String path,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            if (path == null || path.isBlank() || path.contains("..")) {
                return ToolResultBlock.error("file_info: path must be non-empty and must not contain '..'");
            }
            Path repoRoot = workspaceContext.resolveGitPath(repositoryName);
            Path resolved;
            try {
                resolved = SafePathValidator.validateUnderRoot(repoRoot, path);
            } catch (IllegalArgumentException ex) {
                return ToolResultBlock.error(ex.getMessage());
            }
            if (!Files.exists(resolved)) {
                return ToolResultBlock.error("Path does not exist: " + path);
            }
            ToolResultBlock dup = support.checkDuplicate("file_info", path);
            if (dup != null) {
                return dup;
            }
            try {
                BasicFileAttributes attr = Files.readAttributes(resolved, BasicFileAttributes.class);
                String type = attr.isDirectory() ? "directory" : attr.isSymbolicLink() ? "symlink" : "file";
                String size = attr.isDirectory() ? "-" : humanReadableSize(attr.size());
                FileInfoData data = new FileInfoData(
                        path, type, size,
                        attr.lastModifiedTime().toString(),
                        attr.isRegularFile() ? "yes" : "no"
                );
                return support.toToolResult("file_info", path,
                        new CodeGraphToolResult(true, promptManager.renderFramework(INFO_BLOCK_PATH, FileInfoData.class, data)));
            } catch (IOException ex) {
                return ToolResultBlock.error("Failed to read file attributes: " + ex.getMessage());
            }
        });
    }

    // ===== @Tool 4: file_glob =====

    @Tool(
            name = "file_glob",
            description = """
                    Find files matching a glob pattern (e.g. **/*.java, **/pom.xml).
                    Pattern is matched against path relative to the repository root.
                    Returns up to 200 matching paths.
                    """
    )
    public Mono<ToolResultBlock> globFiles(
            @ToolParam(name = "pattern", required = true, description = "Glob pattern, e.g. **/*.java or src/main/**/*.yml.") String pattern,
            @ToolParam(name = "maxResults", required = false, description = "Max results. Default is 200, capped at 1000.") Integer maxResults,
            @ToolParam(name = "baseDirectory", required = false, description = "Base directory. Default is repository root.") String baseDirectory,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            if (pattern == null || pattern.isBlank() || pattern.contains("..")) {
                return ToolResultBlock.error("file_glob: pattern must be non-empty and must not contain '..'");
            }
            int cap = maxResults == null || maxResults <= 0
                    ? DEFAULT_MAX_ENTRIES
                    : Math.min(maxResults, ABSOLUTE_MAX_ENTRIES);
            String base = baseDirectory == null || baseDirectory.isBlank() ? "." : baseDirectory;
            if (base.contains("..")) {
                return ToolResultBlock.error("file_glob: baseDirectory must not contain '..'");
            }
            Path repoRoot = workspaceContext.resolveGitPath(repositoryName);
            Path validatedRoot;
            try {
                validatedRoot = SafePathValidator.validateUnderRoot(repoRoot, base);
            } catch (IllegalArgumentException ex) {
                return ToolResultBlock.error(ex.getMessage());
            }
            // 解析符号链接为真实路径，避免 PathMatcher 在符号链接路径上匹配失败
            Path realRoot = resolveRealPath(validatedRoot);
            if (!Files.isDirectory(realRoot)) {
                return ToolResultBlock.error("Base directory is not a directory or does not exist: " + base);
            }
            log.warn("file_glob: repoRoot={}, realRoot={}, pattern={}, matcherPattern={}", repoRoot, realRoot, pattern, "glob:" + (base.equals(".") ? pattern : base + "/" + pattern));
            String dedupeKey = pattern + "|" + base + "|" + cap;
            ToolResultBlock dup = support.checkDuplicate("file_glob", dedupeKey);
            if (dup != null) {
                return dup;
            }
                        // glob 语法转换：PathMatcher 使用 ** 表示跨目录
            String matcherPattern = "glob:" + (base.equals(".") ? pattern : base + "/" + pattern);
            try (Stream<Path> walk = Files.walk(realRoot, ABSOLUTE_MAX_DEPTH)) {
                var matcher = realRoot.getFileSystem().getPathMatcher(matcherPattern);
                List<String> results = walk
                        .filter(Files::isRegularFile)
                        .filter(matcher::matches)
                        .limit(cap)
                        .map(p -> realRoot.relativize(p).toString())
                        .collect(Collectors.toList());
                // debug
                try (Stream<Path> debugWalk = Files.walk(realRoot, 3)) {
                    List<Path> javaFiles = debugWalk
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .limit(5)
                            .toList();
                    for (Path p : javaFiles) {
                        log.warn("file_glob debug: file={}, relativized={}, matches={}", p, realRoot.relativize(p), matcher.matches(p));
                    }
                    log.warn("file_glob debug: java files sampled={}, matched count={}", javaFiles.size(), results.size());
                }
                if (results.isEmpty()) {
                    return support.toToolResult("file_glob", dedupeKey,
                            new CodeGraphToolResult(true, "No files matched pattern: " + pattern + "."));
                }
                String body = "Found " + results.size() + " matching files:\n"
                        + results.stream().map(p -> "- " + p).collect(Collectors.joining("\n"));
                return support.toToolResult("file_glob", dedupeKey,
                        new CodeGraphToolResult(true, body));
            } catch (IOException ex) {
                return ToolResultBlock.error("Scan failed: " + ex.getMessage());
            }
        });
    }

    // ===== 私有辅助 =====

    /**
     * 解析路径的真实路径（跟随符号链接）。解析失败时返回原路径。
     */
    private Path resolveRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException ex) {
            return path;
        }
    }

    private Optional<String> readWithLimit(Path file, Integer startLine, Integer endLine, long maxBytes, StringBuilder errorHolder) {
        long fileSize;
        try {
            fileSize = Files.size(file);
        } catch (IOException e) {
            errorHolder.append("Read failed: ").append(e.getMessage());
            return Optional.empty();
        }
        if (fileSize > maxBytes) {
            errorHolder.append("File too large (").append(fileSize).append(" bytes), exceeds maxBytes=")
                    .append(maxBytes).append(". Narrow the line range or increase maxBytes.");
            return Optional.empty();
        }
        if (startLine != null && endLine != null && startLine > endLine) {
            errorHolder.append("Invalid line range: startLine=").append(startLine)
                    .append(" > endLine=").append(endLine);
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(file);
            int from = startLine == null ? 1 : Math.max(1, startLine);
            int to = endLine == null ? lines.size() : Math.min(endLine, lines.size());
            if (from > to) {
                errorHolder.append("startLine ").append(from).append(" > endLine ").append(to).append(", no content.");
                return Optional.empty();
            }
            StringBuilder out = new StringBuilder();
            for (int i = from; i <= to; i++) {
                String line = lines.get(i - 1);
                if (line.length() > MAX_LINE_LENGTH) {
                    line = line.substring(0, MAX_LINE_LENGTH) + " ... (line truncated, original length " + lines.get(i - 1).length() + ")";
                }
                out.append(String.format("%4d: %s%n", i, line));
            }
            return Optional.of(out.toString());
        } catch (IOException ex) {
            errorHolder.append("Read failed: ").append(ex.getMessage());
            return Optional.empty();
        }
    }

    private int clampDepth(Integer maxDepth) {
        if (maxDepth == null || maxDepth <= 0) {
            return DEFAULT_MAX_DEPTH;
        }
        return Math.min(maxDepth, ABSOLUTE_MAX_DEPTH);
    }

    private String humanReadableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }

    // ===== jprompt 数据类 =====

    @Value
    @AllArgsConstructor
    public static class FileReadData {
        String path;
        String content;
    }

    @Value
    @AllArgsConstructor
    public static class FileInfoData {
        String path;
        String type;
        String size;
        String lastModified;
        String isRegularFile;
    }

    // ===== 私有提取文本辅助（测试用） =====

    static String extractText(ToolResultBlock block) {
        if (block == null || block.getOutput() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ContentBlock output : block.getOutput()) {
            if (output instanceof TextBlock textBlock && textBlock.getText() != null) {
                builder.append(textBlock.getText());
            }
        }
        return builder.toString();
    }
}
