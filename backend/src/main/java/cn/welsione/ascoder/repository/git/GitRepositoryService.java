package cn.welsione.ascoder.repository.git;

import cn.welsione.ascoder.common.CommandResult;
import cn.welsione.ascoder.common.SafePathValidator;
import cn.welsione.ascoder.repository.RepositoryBranchSourceKind;
import cn.welsione.ascoder.runtime.application.RuntimeSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 封装仓库内 Git 只读查询、同步和 worktree 管理操作。
 *
 * <p>{@code git.timeout-seconds} 在设置页修改后立即对下一次 git 命令生效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitRepositoryService {

    private final GitCommandRunner commandRunner;
    private final RuntimeSettingsService runtimeSettings;

    private long timeoutSeconds() {
        return runtimeSettings.readLong("git.timeout-seconds");
    }

    public List<GitBranchInfo> listBranches(Path repositoryPath) {
        CommandResult result = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                "for-each-ref", "--format=%(refname)|%(objectname)", "refs/heads", "refs/remotes");
        ensureSuccess(result, "读取分支列表失败");
        Map<String, GitBranchInfo> branches = new LinkedHashMap<>();
        result.getOutput().lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .forEach(line -> {
                    String[] parts = line.split("\\|", 2);
                    GitBranchInfo branch = parseLocalBranch(parts[0], parts.length > 1 ? parts[1] : "");
                    if (branch != null) {
                        branches.putIfAbsent(branch.getRefName(), branch);
                    }
                });
        return new ArrayList<>(branches.values());
    }

    public List<GitBranchInfo> listRemoteHeads(Path repositoryPath) {
        CommandResult result = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                "ls-remote", "--heads", "origin");
        ensureSuccess(result, "读取远程分支列表失败");
        return result.getOutput().lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::parseRemoteHead)
                .filter(branch -> branch != null)
                .toList();
    }

    public String commitSha(Path repositoryPath, String branchName) {
        SafePathValidator.sanitizeArg(branchName);
        List<String> refs = candidateRefs(branchName);
        CommandResult result = revParseFirst(repositoryPath, refs);
        if (!result.isSuccess()) {
            fetch(repositoryPath);
            result = revParseFirst(repositoryPath, refs);
        }
        if (!result.isSuccess()) {
            fetchBranch(repositoryPath, branchName);
            result = revParseFirst(repositoryPath, refs);
        }
        ensureSuccess(result, "读取分支 commitSha 失败");
        return result.getOutput().lines().findFirst().orElse("").trim();
    }

    public String commitMessage(Path repositoryPath, String commitSha) {
        SafePathValidator.sanitizeArg(commitSha);
        CommandResult result = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                "log", "-1", "--format=%s", commitSha);
        if (!result.isSuccess()) {
            return null;
        }
        return result.getOutput().lines().findFirst().orElse("").trim();
    }

    /**
     * 读取远端跟踪分支的最新提交；远端分支不存在时返回 null。
     */
    public String remoteCommitSha(Path repositoryPath, String branchName) {
        SafePathValidator.sanitizeArg(branchName);
        String remoteRef = branchName != null && branchName.startsWith("origin/")
                ? branchName
                : "origin/" + branchName;
        CommandResult result = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                "rev-parse", remoteRef);
        if (!result.isSuccess()) {
            return null;
        }
        return result.getOutput().lines().findFirst().orElse("").trim();
    }

    /**
     * 读取指定分支或提交附近最近的提交历史，包含完整 sha、短 sha、Commit Message 和提交时间。
     */
    public List<GitCommitInfo> recentCommits(Path repositoryPath, String ref, int maxCount) {
        SafePathValidator.sanitizeArg(ref);
        int limit = maxCount <= 0 ? 5 : Math.min(maxCount, 50);
        CommandResult result = runRecentCommits(repositoryPath, ref, limit);
        if (!result.isSuccess() && ref != null && !ref.isBlank() && !ref.startsWith("origin/")) {
            result = runRecentCommits(repositoryPath, "origin/" + ref, limit);
        }
        ensureSuccess(result, "读取最近提交失败");
        return result.getOutput().lines()
                .map(this::parseCommitLine)
                .filter(commit -> !commit.getCommitSha().isBlank())
                .toList();
    }

    public void cloneRepository(String remoteUrl, Path targetPath, String branchName) {
        cloneRepository(remoteUrl, targetPath, branchName, null);
    }

    /**
     * 克隆远程仓库，支持通过 onLine 回调实时接收 git 输出行（用于进度追踪）。
     */
    public void cloneRepository(String remoteUrl, Path targetPath, String branchName,
                                java.util.function.Consumer<String> onLine) {
        SafePathValidator.sanitizeArg(remoteUrl);
        SafePathValidator.sanitizeArg(branchName);
        try {
            Files.createDirectories(targetPath.getParent());
        } catch (Exception ex) {
            throw new IllegalStateException("创建仓库父目录失败：" + ex.getMessage(), ex);
        }

        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("clone");
        command.add("--progress");
        if (branchName != null && !branchName.isBlank()) {
            command.add("--branch");
            command.add(branchName.trim());
        }
        command.add(remoteUrl);
        command.add(targetPath.toString());

        CommandResult result = commandRunner.runAsync(command, targetPath.getParent(),
                Duration.ofSeconds(timeoutSeconds()), onLine);
        ensureSuccess(result, "拉取 Git 仓库失败");
    }

    public void fetch(Path repositoryPath) {
        fetch(repositoryPath, null);
    }

    /**
     * 同步远程分支，支持通过 onLine 回调实时接收 git 输出行。
     */
    public void fetch(Path repositoryPath, java.util.function.Consumer<String> onLine) {
        CommandResult result = runAsync(repositoryPath, onLine,
                "git", "-C", repositoryPath.toString(), "fetch", "--all", "--prune", "--progress");
        if (!result.isSuccess()) {
            if (result.getOutput().contains("case-insensitive filesystem")) {
                log.warn("部分分支因大小写冲突跳过：{}", result.getOutput().lines()
                        .filter(l -> l.contains("case-insensitive"))
                        .collect(Collectors.joining("; ")));
            } else {
                ensureSuccess(result, "同步远程分支失败");
            }
        }
    }

    /**
     * 显式拉取目标远端分支，兜底处理本地仓库 refspec 只跟踪部分分支的情况。
     */
    public void fetchBranch(Path repositoryPath, String branchName) {
        SafePathValidator.sanitizeArg(branchName);
        String remoteBranchName = remoteBranchName(branchName);
        CommandResult result = fetchBranchRef(repositoryPath, remoteBranchName, "refs/heads/" + remoteBranchName);
        if (!result.isSuccess()) {
            result = fetchBranchRef(repositoryPath, remoteBranchName, "refs/remotes/origin/" + remoteBranchName);
        }
        ensureSuccess(result, "同步远程分支失败");
    }

    public void pull(Path repositoryPath) {
        pull(repositoryPath, null);
    }

    /**
     * 拉取并合并，支持通过 onLine 回调实时接收 git 输出行。
     */
    public void pull(Path repositoryPath, java.util.function.Consumer<String> onLine) {
        fetch(repositoryPath, onLine);
        String branch = tryGetCurrentBranch(repositoryPath);
        if (branch != null) {
            CommandResult result = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                    "merge", "--ff-only", "refs/remotes/origin/" + branch);
            if (!result.isSuccess()) {
                log.warn("ff-only merge 失败，仅保留 fetch 结果: {}", result.getOutput());
            }
        }
    }

    /**
     * 尝试获取当前分支名，失败时返回 null（如空仓库、detached HEAD）。
     */
    private String tryGetCurrentBranch(Path repositoryPath) {
        CommandResult result = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                "rev-parse", "--abbrev-ref", "HEAD");
        if (!result.isSuccess()) {
            return null;
        }
        String branch = result.getOutput().lines().findFirst().orElse("").trim();
        return branch.isEmpty() || "HEAD".equals(branch) ? null : branch;
    }

    public String currentBranch(Path repositoryPath) {
        CommandResult result = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                "rev-parse", "--abbrev-ref", "HEAD");
        ensureSuccess(result, "读取当前分支失败");
        return result.getOutput().lines().findFirst().orElse("").trim();
    }

    /**
     * 读取仓库 origin 远程的 URL。
     */
    public String getRemoteUrl(Path repositoryPath) {
        CommandResult result = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                "config", "--get", "remote.origin.url");
        if (!result.isSuccess()) {
            return null;
        }
        return result.getOutput().lines().findFirst().orElse("").trim();
    }

    public void createOrUpdateDetachedWorktree(Path repositoryPath, String branchName, String commitSha, Path worktreePath) {
        SafePathValidator.sanitizeArg(branchName);
        SafePathValidator.sanitizeArg(commitSha);
        if (Files.exists(worktreePath.resolve(".git"))) {
            CommandResult checkout = run(worktreePath, "git", "-C", worktreePath.toString(),
                    "checkout", "--detach", commitSha);
            ensureSuccess(checkout, "更新 worktree 失败");
            return;
        }

        try {
            Files.createDirectories(worktreePath.getParent());
        } catch (Exception ex) {
            throw new IllegalStateException("创建 worktree 父目录失败：" + ex.getMessage(), ex);
        }

        CommandResult add = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                "worktree", "add", "--detach", worktreePath.toString(), commitSha);
        ensureSuccess(add, "创建 worktree 失败，branchName=" + branchName);
    }

    public void removeWorktree(Path repositoryPath, Path worktreePath) {
        if (!Files.exists(worktreePath)) {
            return;
        }
        CommandResult result = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                "worktree", "remove", "--force", worktreePath.toString());
        ensureSuccess(result, "删除 worktree 失败");
    }

    /**
     * 读取指定 ref 的提交日志（只读，safe for LLM tool 暴露）。
     *
     * @param repositoryPath 仓库路径
     * @param ref             分支、tag 或 commit（可空，默认 HEAD）
     * @param maxCount        最大返回条数（<=0 视为 20）
     * @param filePath        可选文件路径过滤
     * @return oneline 格式的提交列表
     */
    public String gitLog(Path repositoryPath, String ref, int maxCount, String filePath) {
        SafePathValidator.sanitizeArg(ref);
        SafePathValidator.sanitizeArg(filePath);
        int limit = maxCount <= 0 ? 20 : Math.min(maxCount, 200);
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(repositoryPath.toString());
        cmd.add("log");
        cmd.add("--oneline");
        cmd.add("-n");
        cmd.add(String.valueOf(limit));
        if (ref != null && !ref.isBlank()) {
            cmd.add(ref);
        }
        if (filePath != null && !filePath.isBlank()) {
            cmd.add("--");
            cmd.add(filePath);
        }
        CommandResult result = run(repositoryPath, cmd.toArray(new String[0]));
        ensureSuccess(result, "读取提交日志失败");
        return result.getOutput();
    }

    /**
     * 计算两个 ref 之间的差异（只读，safe for LLM tool 暴露）。
     *
     * @param repositoryPath 仓库路径
     * @param baseRef        基准 ref
     * @param headRef        目标 ref
     * @param filePath       可选文件路径过滤
     * @return 统一 diff 格式输出
     */
    public String gitDiff(Path repositoryPath, String baseRef, String headRef, String filePath) {
        SafePathValidator.sanitizeArg(baseRef);
        SafePathValidator.sanitizeArg(headRef);
        SafePathValidator.sanitizeArg(filePath);
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(repositoryPath.toString());
        cmd.add("diff");
        cmd.add(baseRef + ".." + headRef);
        if (filePath != null && !filePath.isBlank()) {
            cmd.add("--");
            cmd.add(filePath);
        }
        CommandResult result = run(repositoryPath, cmd.toArray(new String[0]));
        ensureSuccess(result, "读取差异失败");
        return result.getOutput();
    }

    /**
     * 查询文件的逐行归属（只读，safe for LLM tool 暴露）。
     *
     * @param repositoryPath 仓库路径
     * @param filePath       仓库内文件路径（必填）
     * @param startLine      起始行（可空）
     * @param endLine        结束行（可空，与 startLine 一起使用）
     * @return git blame 输出
     */
    public String gitBlame(Path repositoryPath, String filePath, Integer startLine, Integer endLine) {
        SafePathValidator.sanitizeArg(filePath);
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(repositoryPath.toString());
        cmd.add("blame");
        if (startLine != null && endLine != null && startLine > 0 && endLine >= startLine) {
            cmd.add("-L");
            cmd.add(startLine + "," + endLine);
        }
        cmd.add("--");
        cmd.add(filePath);
        CommandResult result = run(repositoryPath, cmd.toArray(new String[0]));
        ensureSuccess(result, "git blame 失败");
        return result.getOutput();
    }

    /**
     * 查询指定文件最近一次提交，包含提交人、时间、主题和文件清单。
     */
    public String gitRecentCommit(Path repositoryPath, String filePath) {
        SafePathValidator.sanitizeArg(filePath);
        List<String> cmd = baseGitCommand(repositoryPath);
        cmd.add("log");
        cmd.add("-1");
        cmd.add("--format=commit %H%nshort %h%nauthor %an <%ae>%ndate %cI%nsubject %s");
        if (filePath != null && !filePath.isBlank()) {
            cmd.add("--");
            cmd.add(filePath);
        }
        CommandResult result = run(repositoryPath, cmd.toArray(new String[0]));
        ensureSuccess(result, "读取最近提交失败");
        return result.getOutput();
    }

    /**
     * 查询文件指定行段的逐行责任人，适合追溯某段逻辑是谁在什么时候写入。
     */
    public String gitBlameRange(Path repositoryPath, String filePath, Integer startLine, Integer endLine) {
        SafePathValidator.sanitizeArg(filePath);
        List<String> cmd = baseGitCommand(repositoryPath);
        cmd.add("blame");
        cmd.add("--date=iso");
        if (startLine != null && endLine != null && startLine > 0 && endLine >= startLine) {
            cmd.add("-L");
            cmd.add(startLine + "," + endLine);
        }
        cmd.add("--");
        cmd.add(filePath);
        CommandResult result = run(repositoryPath, cmd.toArray(new String[0]));
        ensureSuccess(result, "git blame 行段追溯失败");
        return result.getOutput();
    }

    /**
     * 查询提交详情，返回作者、提交时间、说明和被修改文件。
     */
    public String gitCommitDetail(Path repositoryPath, String commitSha) {
        SafePathValidator.sanitizeArg(commitSha);
        List<String> cmd = baseGitCommand(repositoryPath);
        cmd.add("show");
        cmd.add("--no-renames");
        cmd.add("--name-status");
        cmd.add("--format=commit %H%nshort %h%nauthor %an <%ae>%ncommitter %cn <%ce>%ndate %cI%nsubject %s%nbody %b");
        cmd.add(commitSha);
        CommandResult result = run(repositoryPath, cmd.toArray(new String[0]));
        ensureSuccess(result, "读取提交详情失败");
        return result.getOutput();
    }

    /**
     * 查询文件提交历史，按时间倒序返回作者、时间和提交说明。
     */
    public String gitFileHistory(Path repositoryPath, String filePath, int maxCount) {
        SafePathValidator.sanitizeArg(filePath);
        int limit = maxCount <= 0 ? 20 : Math.min(maxCount, 100);
        List<String> cmd = baseGitCommand(repositoryPath);
        cmd.add("log");
        cmd.add("-n");
        cmd.add(String.valueOf(limit));
        cmd.add("--format=%h | %cI | %an <%ae> | %s");
        cmd.add("--");
        cmd.add(filePath);
        CommandResult result = run(repositoryPath, cmd.toArray(new String[0]));
        ensureSuccess(result, "读取文件历史失败");
        return result.getOutput();
    }

    /**
     * 查询某次提交自身引入的差异，可选限制到单个文件。
     */
    public String gitDiffForCommit(Path repositoryPath, String commitSha, String filePath, boolean statOnly) {
        SafePathValidator.sanitizeArg(commitSha);
        SafePathValidator.sanitizeArg(filePath);
        List<String> cmd = baseGitCommand(repositoryPath);
        cmd.add("show");
        cmd.add("--no-renames");
        if (statOnly) {
            cmd.add("--stat");
            cmd.add("--format=commit %H%nshort %h%nauthor %an <%ae>%ndate %cI%nsubject %s");
        } else {
            cmd.add("--format=commit %H%nshort %h%nauthor %an <%ae>%ndate %cI%nsubject %s");
        }
        cmd.add(commitSha);
        if (filePath != null && !filePath.isBlank()) {
            cmd.add("--");
            cmd.add(filePath);
        }
        CommandResult result = run(repositoryPath, cmd.toArray(new String[0]));
        ensureSuccess(result, "读取提交差异失败");
        return result.getOutput();
    }

    private CommandResult run(Path workingDirectory, String... command) {
        return commandRunner.run(Arrays.asList(command), workingDirectory, Duration.ofSeconds(timeoutSeconds()));
    }

    /** 带行回调的命令执行，用于实时追踪 git 进度。 */
    private CommandResult runAsync(Path workingDirectory, java.util.function.Consumer<String> onLine, String... command) {
        return commandRunner.runAsync(Arrays.asList(command), workingDirectory,
                Duration.ofSeconds(timeoutSeconds()), onLine);
    }

    private List<String> baseGitCommand(Path repositoryPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(repositoryPath.toString());
        return cmd;
    }

    private CommandResult runRecentCommits(Path repositoryPath, String ref, int limit) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(repositoryPath.toString());
        cmd.add("log");
        cmd.add("-n");
        cmd.add(String.valueOf(limit));
        cmd.add("--format=%H%x1f%h%x1f%s%x1f%cI");
        if (ref != null && !ref.isBlank()) {
            cmd.add(ref);
        }
        return run(repositoryPath, cmd.toArray(new String[0]));
    }

    private CommandResult revParseFirst(Path repositoryPath, List<String> refs) {
        CommandResult result = null;
        for (String ref : refs) {
            result = run(repositoryPath, "git", "-C", repositoryPath.toString(),
                    "rev-parse", ref);
            if (result.isSuccess()) {
                return result;
            }
        }
        return result;
    }

    private List<String> candidateRefs(String branchName) {
        String ref = branchName == null ? "" : branchName.trim();
        if (ref.startsWith("origin/")) {
            return List.of(ref, ref.substring("origin/".length()));
        }
        return List.of(ref, "origin/" + ref);
    }

    private CommandResult fetchBranchRef(Path repositoryPath, String remoteBranchName, String sourceRef) {
        return run(repositoryPath, "git", "-C", repositoryPath.toString(),
                "fetch", "origin",
                "+" + sourceRef + ":refs/remotes/origin/" + remoteBranchName,
                "--prune");
    }

    private String remoteBranchName(String branchName) {
        String ref = branchName == null ? "" : branchName.trim();
        return ref.startsWith("origin/") ? ref.substring("origin/".length()) : ref;
    }

    private GitCommitInfo parseCommitLine(String line) {
        String[] parts = line.split(String.valueOf((char) 31), -1);
        return new GitCommitInfo(
                parts.length > 0 ? parts[0].trim() : "",
                parts.length > 1 ? parts[1].trim() : "",
                parts.length > 2 ? parts[2].trim() : "",
                parts.length > 3 ? parts[3].trim() : ""
        );
    }

    private void ensureSuccess(CommandResult result, String message) {
        if (!result.isSuccess()) {
            String output = result.getOutput();
            String hint = detectAuthHint(output);
            throw new IllegalStateException(message + "：" + output + hint);
        }
    }

    /**
     * 检测 Git 输出中的认证失败特征，返回用户友好的修复提示。
     */
    private String detectAuthHint(String output) {
        if (output == null) {
            return "";
        }
        if (output.contains("could not read Username")
                || output.contains("Authentication failed")
                || output.contains("could not read Password")
                || output.contains("fatal: unable to access") && output.contains("403")) {
            return "\n提示：Git 认证失败，请检查仓库凭据配置。"
                    + "Docker 部署请设置 GIT_TOKEN/GIT_USERNAME/GIT_EXTRA_HOSTS 环境变量，"
                    + "或在仓库配置中添加凭据。";
        }
        return "";
    }

    private GitBranchInfo parseLocalBranch(String refName, String commitSha) {
        if (refName.startsWith("refs/heads/")) {
            String branchName = refName.substring("refs/heads/".length());
            return new GitBranchInfo(branchName, refName, commitSha, null, RepositoryBranchSourceKind.LOCAL_HEAD);
        }
        if (refName.startsWith("refs/remotes/")) {
            String remoteRef = refName.substring("refs/remotes/".length());
            int slash = remoteRef.indexOf('/');
            if (slash <= 0 || slash == remoteRef.length() - 1) {
                return null;
            }
            String remoteName = remoteRef.substring(0, slash);
            String branchName = remoteRef.substring(slash + 1);
            if ("HEAD".equals(branchName)) {
                return null;
            }
            return new GitBranchInfo(
                    branchName,
                    refName,
                    commitSha,
                    remoteName,
                    RepositoryBranchSourceKind.REMOTE_TRACKING
            );
        }
        if (refName.startsWith("origin/")) {
            String branchName = refName.substring("origin/".length());
            return "HEAD".equals(branchName)
                    ? null
                    : new GitBranchInfo(
                            branchName,
                            "refs/remotes/" + refName,
                            commitSha,
                            "origin",
                            RepositoryBranchSourceKind.REMOTE_TRACKING
                    );
        }
        if (!refName.isBlank()) {
            return new GitBranchInfo(
                    refName,
                    "refs/heads/" + refName,
                    commitSha,
                    null,
                    RepositoryBranchSourceKind.LOCAL_HEAD
            );
        }
        return null;
    }

    private GitBranchInfo parseRemoteHead(String line) {
        String[] parts = line.split("\\s+", 2);
        if (parts.length < 2 || !parts[1].startsWith("refs/heads/")) {
            return null;
        }
        String refName = parts[1].trim();
        String branchName = refName.substring("refs/heads/".length());
        return new GitBranchInfo(branchName, refName, parts[0].trim(), "origin", RepositoryBranchSourceKind.REMOTE_HEAD);
    }
}
