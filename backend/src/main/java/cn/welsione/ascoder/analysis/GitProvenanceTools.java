package cn.welsione.ascoder.analysis;

import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Git 证据追溯工具集，为 Agent 提供只读的提交、责任人和文件历史查询能力。
 */
@Slf4j
public class GitProvenanceTools {

    private final GitRepositoryService gitRepositoryService;
    private final CodeGraphWorkspaceContext workspaceContext;
    private final CodeGraphToolSupport support;

    public GitProvenanceTools(
            GitRepositoryService gitRepositoryService,
            CodeGraphWorkspaceContext workspaceContext,
            AtomicReference<String> codeContext,
            String metadata
    ) {
        this.gitRepositoryService = gitRepositoryService;
        this.workspaceContext = workspaceContext;
        this.support = new CodeGraphToolSupport(codeContext, metadata);
    }

    /**
     * 一次性注册类内所有 @Tool 方法到 Toolkit。
     */
    public void registerAll(Toolkit toolkit) {
        toolkit.registration().tool(this).apply();
        log.debug("GitProvenanceTools 注册完成，共 5 个 @Tool 方法");
    }

    @Tool(
            name = "git_recent_commit",
            description = """
                    Find the latest commit that touched a file, including author, email,
                    commit time, subject, and commit SHA. Use this when the user asks
                    who most recently changed a piece of logic.
                    """
    )
    public Mono<ToolResultBlock> recentCommit(
            @ToolParam(name = "filePath", required = false, description = "Optional file path within the repository. Must not contain '..'. Pass paths relative to repository root; an accidental repository-name prefix is stripped automatically.") String filePath,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            String safePath = optionalSafePath(filePath, "git_recent_commit");
            if (safePath == null && filePath != null && !filePath.isBlank()) {
                return ToolResultBlock.error("git_recent_commit: filePath must be a relative path without '..' or unsafe characters, under 500 chars.");
            }
            String normalizedPath = workspaceContext.normalizeRepoFilePath(repositoryName, safePath);
            String dedupeKey = normalizedPath == null ? "HEAD" : normalizedPath;
            ToolResultBlock duplicate = support.checkDuplicate("git_recent_commit", dedupeKey);
            if (duplicate != null) {
                return duplicate;
            }
            Path repoPath = workspaceContext.resolveGitPath(repositoryName);
            String output = gitRepositoryService.gitRecentCommit(repoPath, normalizedPath);
            return support.toToolResult(
                    "git_recent_commit",
                    dedupeKey,
                    new CodeGraphToolResult(true, output)
            );
        });
    }

    @Tool(
            name = "git_blame_range",
            description = """
                    Show line attribution for a specific file range with author and date.
                    Use this to trace who wrote or last changed the relevant lines.
                    """
    )
    public Mono<ToolResultBlock> blameRange(
            @ToolParam(name = "filePath", required = true, description = "File path within the repository. Must not contain '..'. Pass paths relative to repository root; an accidental repository-name prefix is stripped automatically.") String filePath,
            @ToolParam(name = "startLine", required = false, description = "Starting line number, 1-based.") Integer startLine,
            @ToolParam(name = "endLine", required = false, description = "Ending line number, 1-based and >= startLine.") Integer endLine,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            String safePath = requiredSafePath(filePath, "git_blame_range");
            if (safePath == null) {
                if (filePath == null || filePath.isBlank()) {
                    return ToolResultBlock.error("git_blame_range: filePath is required.");
                }
                return ToolResultBlock.error("git_blame_range: filePath must be a relative path without '..' or unsafe characters, under 500 chars.");
            }
            if (!validLineRange(startLine, endLine)) {
                return ToolResultBlock.error("git_blame_range: startLine and endLine must be >= 1, and endLine >= startLine. Got startLine=%d, endLine=%d.".formatted(startLine, endLine));
            }
            String normalizedPath = workspaceContext.normalizeRepoFilePath(repositoryName, safePath);
            String dedupeKey = "%s|%s|%s".formatted(normalizedPath, startLine == null ? "" : startLine, endLine == null ? "" : endLine);
            ToolResultBlock duplicate = support.checkDuplicate("git_blame_range", dedupeKey);
            if (duplicate != null) {
                return duplicate;
            }
            Path repoPath = workspaceContext.resolveGitPath(repositoryName);
            String output = gitRepositoryService.gitBlameRange(repoPath, normalizedPath, startLine, endLine);
            return support.toToolResult(
                    "git_blame_range",
                    dedupeKey,
                    new CodeGraphToolResult(true, output)
            );
        });
    }

    @Tool(
            name = "git_commit_detail",
            description = """
                    Show commit provenance details: author, committer, time, subject,
                    body, and changed files. Use this before naming a commit as evidence.
                    """
    )
    public Mono<ToolResultBlock> commitDetail(
            @ToolParam(name = "commitSha", required = true, description = "Commit SHA or safe ref. Must match [A-Za-z0-9_./-~^]{1,200}.") String commitSha,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            String safeCommit = sanitizeRef(commitSha);
            if (safeCommit == null) {
                return ToolResultBlock.error("git_commit_detail: commitSha must match [A-Za-z0-9_./-~^], be 1-200 characters, and not be empty.");
            }
            ToolResultBlock duplicate = support.checkDuplicate("git_commit_detail", safeCommit);
            if (duplicate != null) {
                return duplicate;
            }
            Path repoPath = workspaceContext.resolveGitPath(repositoryName);
            String output = gitRepositoryService.gitCommitDetail(repoPath, safeCommit);
            return support.toToolResult(
                    "git_commit_detail",
                    safeCommit,
                    new CodeGraphToolResult(true, output)
            );
        });
    }

    @Tool(
            name = "git_file_history",
            description = """
                    Show commit history for one file with commit SHA, commit time,
                    author, email, and subject. Use this to find who introduced or
                    repeatedly changed a business rule.
                    """
    )
    public Mono<ToolResultBlock> fileHistory(
            @ToolParam(name = "filePath", required = true, description = "File path within the repository. Must not contain '..'. Pass paths relative to repository root; an accidental repository-name prefix is stripped automatically.") String filePath,
            @ToolParam(name = "maxCount", required = false, description = "Maximum number of commits. Default is 20, capped at 100.") Integer maxCount,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            String safePath = requiredSafePath(filePath, "git_file_history");
            if (safePath == null) {
                if (filePath == null || filePath.isBlank()) {
                    return ToolResultBlock.error("git_file_history: filePath is required.");
                }
                return ToolResultBlock.error("git_file_history: filePath must be a relative path without '..' or unsafe characters, under 500 chars.");
            }
            String normalizedPath = workspaceContext.normalizeRepoFilePath(repositoryName, safePath);
            int limit = maxCount == null ? 20 : maxCount;
            String dedupeKey = "%s|%s".formatted(normalizedPath, limit);
            ToolResultBlock duplicate = support.checkDuplicate("git_file_history", dedupeKey);
            if (duplicate != null) {
                return duplicate;
            }
            Path repoPath = workspaceContext.resolveGitPath(repositoryName);
            String output = gitRepositoryService.gitFileHistory(repoPath, normalizedPath, limit);
            return support.toToolResult(
                    "git_file_history",
                    dedupeKey,
                    new CodeGraphToolResult(true, output)
            );
        });
    }

    @Tool(
            name = "git_diff_for_commit",
            description = """
                    Show the changes introduced by one commit. Set statOnly=true for
                    a compact summary, or false for unified diff evidence.
                    """
    )
    public Mono<ToolResultBlock> diffForCommit(
            @ToolParam(name = "commitSha", required = true, description = "Commit SHA or safe ref. Must match [A-Za-z0-9_./-~^]{1,200}.") String commitSha,
            @ToolParam(name = "filePath", required = false, description = "Optional file path within the repository. Must not contain '..'. Pass paths relative to repository root; an accidental repository-name prefix is stripped automatically.") String filePath,
            @ToolParam(name = "statOnly", required = false, description = "Whether to return only summary stats. Defaults to true.") Boolean statOnly,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            String safeCommit = sanitizeRef(commitSha);
            String safePath = optionalSafePath(filePath, "git_diff_for_commit");
            if (safeCommit == null) {
                return ToolResultBlock.error("git_diff_for_commit: commitSha must match [A-Za-z0-9_./-~^], be 1-200 characters, and not be empty.");
            }
            if (safePath == null && filePath != null && !filePath.isBlank()) {
                return ToolResultBlock.error("git_diff_for_commit: filePath must be a relative path without '..' or unsafe characters, under 500 chars.");
            }
            String normalizedPath = workspaceContext.normalizeRepoFilePath(repositoryName, safePath);
            boolean compact = statOnly == null || statOnly;
            String dedupeKey = "%s|%s|%s".formatted(safeCommit, normalizedPath == null ? "" : normalizedPath, compact);
            ToolResultBlock duplicate = support.checkDuplicate("git_diff_for_commit", dedupeKey);
            if (duplicate != null) {
                return duplicate;
            }
            Path repoPath = workspaceContext.resolveGitPath(repositoryName);
            String output = gitRepositoryService.gitDiffForCommit(repoPath, safeCommit, normalizedPath, compact);
            return support.toToolResult(
                    "git_diff_for_commit",
                    dedupeKey,
                    new CodeGraphToolResult(true, output)
            );
        });
    }

    private String requiredSafePath(String filePath, String toolName) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        return optionalSafePath(filePath, toolName);
    }

    private String optionalSafePath(String filePath, String toolName) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        String trimmed = filePath.trim();

        // 绝对路径自动去掉前导 /
        if (trimmed.startsWith("/")) {
            String original = trimmed;
            trimmed = trimmed.substring(1);
            log.debug("{} 自动去掉前导 /：{} → {}", toolName, original, trimmed);
        }

        // 路径遍历检测：Path.normalize() 后仍包含 .. 路径段才拒绝
        if (containsPathTraversal(trimmed) || trimmed.length() > 500) {
            log.warn("{} 拒绝不安全文件路径：{}", toolName, filePath);
            return null;
        }
        try {
            cn.welsione.ascoder.common.SafePathValidator.sanitizeArg(trimmed);
        } catch (IllegalArgumentException ex) {
            log.warn("{} 拒绝不安全文件路径：{}", toolName, filePath);
            return null;
        }
        return trimmed;
    }

    /**
     * 路径段级别的遍历检测：{@code Path.normalize()} 后仍包含 {@code ..} 才视为路径遍历。
     */
    private boolean containsPathTraversal(String relativePath) {
        String normalized = java.nio.file.Path.of(relativePath).normalize().toString();
        return normalized.contains("..");
    }

    private boolean validLineRange(Integer startLine, Integer endLine) {
        if (startLine == null || endLine == null) {
            return true;
        }
        return startLine > 0 && endLine >= startLine;
    }

    /**
     * 校验 Git ref 字符集，防止将 shell 元字符传入 Git 命令参数。
     */
    private String sanitizeRef(String ref) {
        if (ref == null) {
            return null;
        }
        String trimmed = ref.trim();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            return null;
        }
        if (!trimmed.matches("^[A-Za-z0-9_./\\-~^]+$")) {
            return null;
        }
        return trimmed;
    }
}
