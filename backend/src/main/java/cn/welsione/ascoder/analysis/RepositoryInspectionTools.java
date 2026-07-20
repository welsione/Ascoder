package cn.welsione.ascoder.analysis;

import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.FilePathSanitizer;
import cn.welsione.ascoder.repository.git.GitBranchInfo;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 仓库检查工具集，暴露 5 个 Git 只读 @Tool 方法：list_branches / get_commit /
 * show_log / diff / blame。所有方法均通过 CodeGraphToolSupport 共享去重 + 上下文累积。
 * <p>
 * 写操作（clone / fetch / pull / worktree add/remove）不在工具集中暴露，仍由
 * GitRepositoryService 直接被 RepositoryService / BranchWorkspaceService 调用。
 */
@Slf4j
public class RepositoryInspectionTools {

    private final GitRepositoryService gitRepositoryService;
    private final CodeGraphWorkspaceContext workspaceContext;
    private final FilePathSanitizer filePathSanitizer;
    private final CodeGraphToolSupport support;

    public RepositoryInspectionTools(
            GitRepositoryService gitRepositoryService,
            CodeGraphWorkspaceContext workspaceContext,
            FilePathSanitizer filePathSanitizer,
            AtomicReference<String> codeContext,
            String metadata
    ) {
        this.gitRepositoryService = gitRepositoryService;
        this.workspaceContext = workspaceContext;
        this.filePathSanitizer = filePathSanitizer;
        this.support = new CodeGraphToolSupport(codeContext, metadata);
    }

    /**
     * 一次性注册类内所有 @Tool 方法到 Toolkit。
     */
    public void registerAll(Toolkit toolkit) {
        toolkit.registration().tool(this).apply();
        log.debug("RepositoryInspectionTools 注册完成，共 5 个 @Tool 方法");
    }

    @Tool(
            name = "git_list_branches",
            description = """
                    List all local and remote branches with their commit SHAs.
                    Use this to understand which branches are available in the workspace
                    before inspecting history or comparing changes.
                    """
    )
    public Mono<ToolResultBlock> listBranches(
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            ToolResultBlock duplicate = support.checkDuplicate("git_list_branches", repositoryName);
            if (duplicate != null) {
                return duplicate;
            }
            Path repoPath = workspaceContext.resolveGitPath(repositoryName);
            List<GitBranchInfo> branches = gitRepositoryService.listBranches(repoPath);
            String output = branches.stream()
                    .map(b -> "- " + b.getBranchName() + " " + b.getCommitSha())
                    .collect(Collectors.joining("\n"));
            return support.toToolResult(
                    "git_list_branches",
                    repositoryName == null ? "" : repositoryName,
                    new CodeGraphToolResult(true, output)
            );
        });
    }

    @Tool(
            name = "git_get_commit",
            description = """
                    Get the commit SHA for a branch, tag, or ref.
                    Falls back to origin/<ref> if the local ref does not exist.
                    """
    )
    public Mono<ToolResultBlock> getCommit(
            @ToolParam(name = "ref", required = true, description = "Branch, tag, or ref name. Must match [A-Za-z0-9_./-]{1,200}.") String ref,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            if (ref == null || ref.isBlank()) {
                return ToolResultBlock.error("git_get_commit: ref is required.");
            }
            String safeRef = sanitizeRef(ref);
            if (safeRef == null) {
                return ToolResultBlock.error("git_get_commit: ref must match [A-Za-z0-9_./-~^], be 1-200 characters, and not be empty. Got: " + ref);
            }
            ToolResultBlock duplicate = support.checkDuplicate("git_get_commit", safeRef);
            if (duplicate != null) {
                return duplicate;
            }
            Path repoPath = workspaceContext.resolveGitPath(repositoryName);
            String sha = gitRepositoryService.commitSha(repoPath, safeRef);
            return support.toToolResult(
                    "git_get_commit",
                    safeRef,
                    new CodeGraphToolResult(true, sha)
            );
        });
    }

    @Tool(
            name = "git_show_log",
            description = """
                    Show recent commit log in oneline format.
                    Useful for understanding recent changes to a branch or file.
                    """
    )
    public Mono<ToolResultBlock> showLog(
            @ToolParam(name = "ref", required = false, description = "Branch, tag, or commit. Default is HEAD.") String ref,
            @ToolParam(name = "maxCount", required = false, description = "Maximum number of commits. Default is 20, capped at 200.") Integer maxCount,
            @ToolParam(name = "filePath", required = false, description = "Optional file path filter (pathspec). Pass paths relative to repository root; an accidental repository-name prefix is stripped automatically.") String filePath,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            String safeRef = ref == null || ref.isBlank() ? null : sanitizeRef(ref);
            if (ref != null && !ref.isBlank() && safeRef == null) {
                return ToolResultBlock.error("git_show_log: ref must match [A-Za-z0-9_./-~^] and be 1-200 characters.");
            }
            String safePath = filePathSanitizer.sanitizeOrNull(filePath);
            if (safePath == null && filePath != null && !filePath.isBlank()) {
                return ToolResultBlock.error("git_show_log: filePath must be a relative path without '..' or unsafe characters, under 500 chars.");
            }
            String normalizedPath = workspaceContext.normalizeRepoFilePath(repositoryName, safePath);
            int limit = maxCount == null ? 20 : maxCount;
            String dedupeKey = "%s|%s|%s".formatted(
                    safeRef == null ? "HEAD" : safeRef,
                    limit,
                    normalizedPath == null ? "" : normalizedPath
            );
            ToolResultBlock duplicate = support.checkDuplicate("git_show_log", dedupeKey);
            if (duplicate != null) {
                return duplicate;
            }
            Path repoPath = workspaceContext.resolveGitPath(repositoryName);
            String output = gitRepositoryService.gitLog(repoPath, safeRef, limit, normalizedPath);
            return support.toToolResult(
                    "git_show_log",
                    dedupeKey,
                    new CodeGraphToolResult(true, output)
            );
        });
    }

    @Tool(
            name = "git_diff",
            description = """
                    Show the diff between two refs (base..head).
                    Use this to understand what changed between two versions.
                    """
    )
    public Mono<ToolResultBlock> diff(
            @ToolParam(name = "baseRef", required = true, description = "Base ref (e.g. main, HEAD~1).") String baseRef,
            @ToolParam(name = "headRef", required = true, description = "Head ref (e.g. feature-branch, commit SHA).") String headRef,
            @ToolParam(name = "filePath", required = false, description = "Optional file path filter (pathspec). Pass paths relative to repository root; an accidental repository-name prefix is stripped automatically.") String filePath,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            String safeBase = sanitizeRef(baseRef);
            String safeHead = sanitizeRef(headRef);
            if (safeBase == null || safeHead == null) {
                return ToolResultBlock.error("git_diff: baseRef and headRef must match [A-Za-z0-9_./-~^], be 1-200 characters, and not be empty.");
            }
            String safePath = filePathSanitizer.sanitizeOrNull(filePath);
            if (safePath == null && filePath != null && !filePath.isBlank()) {
                return ToolResultBlock.error("git_diff: filePath must be a relative path without '..' or unsafe characters, under 500 chars.");
            }
            String normalizedPath = workspaceContext.normalizeRepoFilePath(repositoryName, safePath);
            String dedupeKey = "%s..%s|%s".formatted(safeBase, safeHead, normalizedPath == null ? "" : normalizedPath);
            ToolResultBlock duplicate = support.checkDuplicate("git_diff", dedupeKey);
            if (duplicate != null) {
                return duplicate;
            }
            Path repoPath = workspaceContext.resolveGitPath(repositoryName);
            String output = gitRepositoryService.gitDiff(repoPath, safeBase, safeHead, normalizedPath);
            return support.toToolResult(
                    "git_diff",
                    dedupeKey,
                    new CodeGraphToolResult(true, output)
            );
        });
    }

    @Tool(
            name = "git_blame",
            description = """
                    Show line-by-line attribution for a file.
                    Use this to understand who changed a line and when.
                    """
    )
    public Mono<ToolResultBlock> blame(
            @ToolParam(name = "filePath", required = true, description = "File path within the repository (must not contain ..). Pass paths relative to repository root; an accidental repository-name prefix is stripped automatically.") String filePath,
            @ToolParam(name = "startLine", required = false, description = "Starting line number (1-based, optional).") Integer startLine,
            @ToolParam(name = "endLine", required = false, description = "Ending line number (1-based, optional, must be >= startLine).") Integer endLine,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            if (filePath == null || filePath.isBlank()) {
                return ToolResultBlock.error("git_blame: filePath is required.");
            }
            String safePath = filePathSanitizer.sanitizeOrNull(filePath);
            if (safePath == null) {
                return ToolResultBlock.error("git_blame: filePath must be a relative path without '..' or unsafe characters, under 500 chars.");
            }
            String normalizedPath = workspaceContext.normalizeRepoFilePath(repositoryName, safePath);
            String dedupeKey = "%s|%s|%s".formatted(
                    normalizedPath,
                    startLine == null ? "" : startLine,
                    endLine == null ? "" : endLine
            );
            ToolResultBlock duplicate = support.checkDuplicate("git_blame", dedupeKey);
            if (duplicate != null) {
                return duplicate;
            }
            Path repoPath = workspaceContext.resolveGitPath(repositoryName);
            String output = gitRepositoryService.gitBlame(repoPath, normalizedPath, startLine, endLine);
            return support.toToolResult(
                    "git_blame",
                    dedupeKey,
                    new CodeGraphToolResult(true, output)
            );
        });
    }

    /**
     * 校验 ref 字符集：仅允许字母数字、点、下划线、横线、斜线；长度 1-200。
     * 防止 LLM 注入 shell 元字符（&、;、|、$、>、< 等）。
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
