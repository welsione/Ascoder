package cn.welsione.ascoder.analysis;

import cn.welsione.ascoder.agent.domain.AgentRequest;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 工作区路径解析，同时服务 CodeGraph 工具和 Git 工具。
 *
 * <p>CodeGraph 工具需要项目空间根路径（索引建在整个空间上），
 * Git 工具需要各成员仓库的 worktree 路径（git 命令必须在仓库内执行）。</p>
 */
public class CodeGraphWorkspaceContext {

    private final Path projectSpaceRoot;
    private final List<AgentRequest.RepositoryContext> repositories;
    private final AgentRequest.RepositoryContext primary;

    public CodeGraphWorkspaceContext(Path projectSpaceRoot, List<AgentRequest.RepositoryContext> repositories) {
        this.projectSpaceRoot = projectSpaceRoot;
        this.repositories = repositories == null ? List.of() : repositories;
        this.primary = this.repositories.stream()
                .filter(AgentRequest.RepositoryContext::isPrimary)
                .findFirst()
                .orElseGet(() -> this.repositories.isEmpty() ? null : this.repositories.get(0));
    }

    public static CodeGraphWorkspaceContext single(Path repositoryPath) {
        AgentRequest.RepositoryContext repository = new AgentRequest.RepositoryContext(
                null, "repository", null, null, null,
                repositoryPath.toString(), null, "repository", true
        );
        return new CodeGraphWorkspaceContext(repositoryPath, List.of(repository));
    }

    /**
     * 解析 CodeGraph 操作路径。项目空间模式下返回项目空间根路径，
     * 单仓库模式下返回仓库路径。
     */
    public Path resolveCodeGraphPath() {
        return projectSpaceRoot;
    }

    /**
     * 解析 CodeGraph 操作路径并校验 repositoryName 是否有效。
     * 当 repositoryName 不匹配任何仓库时，{@link #select} 会抛出 IllegalArgumentException。
     */
    public Path resolveCodeGraphPath(String repositoryName) {
        select(repositoryName);
        return projectSpaceRoot;
    }

    /**
     * 解析 Git 操作路径。返回指定仓库的 worktree 路径（软链接或 worktree 目录），
     * 默认返回主仓库路径。单仓库模式下等同于仓库路径。
     */
    public Path resolveGitPath(String repositoryName) {
        AgentRequest.RepositoryContext repository = select(repositoryName);
        if (repository == null || repository.getWorkspacePath() == null || repository.getWorkspacePath().isBlank()) {
            throw new IllegalArgumentException("未找到可用仓库路径，请指定 repositoryName");
        }
        return Path.of(repository.getWorkspacePath());
    }

    /**
     * @deprecated 使用 {@link #resolveCodeGraphPath()} 或 {@link #resolveGitPath(String)}
     */
    @Deprecated
    public Path resolve(String repositoryName) {
        return resolveCodeGraphPath();
    }

    public String queryLabel(String repositoryName, String query) {
        AgentRequest.RepositoryContext repository = select(repositoryName);
        String selected = repository == null ? "unknown" : repository.getRepositoryName();
        String actualQuery = query == null || query.isBlank() ? "(empty)" : query;
        return "repository=%s query=%s".formatted(selected, actualQuery);
    }

    /**
     * 去除 LLM 传入文件路径中可能带有的仓库名前缀。
     *
     * <p>项目空间模式下，codegraph_files 返回的路径形如 {@code repo-a/src/Foo.java}，
     * 但 git 命令在 {@code <spaceRoot>/repo-a} 目录执行，需要传入 {@code src/Foo.java}。
     * 本方法依次尝试剥离 repositoryName 和 worktree 末段名称作为前缀。</p>
     */
    public String normalizeRepoFilePath(String repositoryName, String filePath) {
        if (filePath == null) {
            return null;
        }
        if (filePath.isBlank() || repositories.isEmpty()) {
            return filePath;
        }
        String stripped = tryStripPrefix(filePath, repositoryName);
        if (stripped != null) {
            return stripped;
        }
        AgentRequest.RepositoryContext repo = select(repositoryName);
        if (repo != null && repo.getWorkspacePath() != null) {
            Path lastSegment = Path.of(repo.getWorkspacePath()).getFileName();
            if (lastSegment != null) {
                stripped = tryStripPrefix(filePath, lastSegment.toString());
                if (stripped != null) {
                    return stripped;
                }
            }
        }
        return filePath;
    }

    /**
     * 如果 filePath 以 prefix/ 开头（且剥离后有剩余内容），返回剩余部分；否则返回 null。
     */
    private String tryStripPrefix(String filePath, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        String prefixSlash = prefix + "/";
        if (filePath.startsWith(prefixSlash) && filePath.length() > prefixSlash.length()) {
            return filePath.substring(prefixSlash.length());
        }
        return null;
    }

    private AgentRequest.RepositoryContext select(String repositoryName) {
        if (repositoryName == null || repositoryName.isBlank()) {
            return primary;
        }
        String key = repositoryName.trim().toLowerCase(Locale.ROOT);
        return repositories.stream()
                .filter(repository -> matches(repository, key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("项目空间不存在仓库：" + repositoryName));
    }

    private boolean matches(AgentRequest.RepositoryContext repository, String key) {
        return equalsKey(repository.getRepositoryName(), key)
                || equalsKey(repository.getRole(), key)
                || repository.getRepositoryId() != null && repository.getRepositoryId().toString().equals(key)
                || repository.getBranchWorkspaceId() != null && repository.getBranchWorkspaceId().toString().equals(key);
    }

    private boolean equalsKey(String value, String key) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).equals(key);
    }
}
