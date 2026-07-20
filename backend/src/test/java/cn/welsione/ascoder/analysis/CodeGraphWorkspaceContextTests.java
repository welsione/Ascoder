package cn.welsione.ascoder.analysis;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 CodeGraph 工作空间上下文在项目空间和单仓库场景下的路径解析行为。
 */
class CodeGraphWorkspaceContextTests {

    private static final Path SPACE_ROOT = Path.of("/tmp/space");
    private static final Path REPO_A_WORKTREE = Path.of("/tmp/worktrees/repo-a");
    private static final Path REPO_B_WORKTREE = Path.of("/tmp/worktrees/repo-b");

    @Test
    void resolveCodeGraphPath_returnsProjectSpaceRoot() {
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        assertThat(ctx.resolveCodeGraphPath()).isEqualTo(SPACE_ROOT);
    }

    @Test
    void resolveGitPath_defaultsToPrimaryRepository() {
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        Path path = ctx.resolveGitPath(null);

        assertThat(path).isEqualTo(REPO_A_WORKTREE);
    }

    @Test
    void resolveGitPath_defaultsToPrimaryRepository_blankName() {
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        Path path = ctx.resolveGitPath("   ");

        assertThat(path).isEqualTo(REPO_A_WORKTREE);
    }

    @Test
    void resolveGitPath_selectsByName() {
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        Path path = ctx.resolveGitPath("repo-b");

        assertThat(path).isEqualTo(REPO_B_WORKTREE);
    }

    @Test
    void resolveGitPath_selectsByRole() {
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        Path path = ctx.resolveGitPath("frontend");

        assertThat(path).isEqualTo(REPO_B_WORKTREE);
    }

    @Test
    void resolveGitPath_throwsOnUnknownName() {
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        assertThatThrownBy(() -> ctx.resolveGitPath("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("项目空间不存在仓库");
    }

    @Test
    void resolveGitPath_throwsOnEmptyRepositories() {
        CodeGraphWorkspaceContext ctx = new CodeGraphWorkspaceContext(SPACE_ROOT, List.of());

        assertThatThrownBy(() -> ctx.resolveGitPath(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到可用仓库路径");
    }

    @Test
    void single_returnsSamePathForBothResolutions() {
        Path repoPath = Path.of("/tmp/my-repo");
        CodeGraphWorkspaceContext ctx = CodeGraphWorkspaceContext.single(repoPath);

        assertThat(ctx.resolveCodeGraphPath()).isEqualTo(repoPath);
        assertThat(ctx.resolveGitPath(null)).isEqualTo(repoPath);
    }

    @Test
    void queryLabel_showsRepositoryName() {
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        assertThat(ctx.queryLabel(null, "main")).isEqualTo("repository=repo-a query=main");
        assertThat(ctx.queryLabel("repo-b", "dev")).isEqualTo("repository=repo-b query=dev");
    }

    @Test
    void queryLabel_unknownWhenNoRepositories() {
        CodeGraphWorkspaceContext ctx = new CodeGraphWorkspaceContext(SPACE_ROOT, List.of());

        assertThat(ctx.queryLabel(null, "test")).isEqualTo("repository=unknown query=test");
    }

    @Test
    void normalizeRepoFilePath_stripsRepositoryNamePrefix() {
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        assertThat(ctx.normalizeRepoFilePath("repo-a", "repo-a/src/Foo.java"))
                .isEqualTo("src/Foo.java");
    }

    @Test
    void normalizeRepoFilePath_stripsWorkspaceLastSegmentPrefix() {
        // LLM 看到 path=/tmp/worktrees/repo-a，可能把文件写成 repo-a/src/Foo.java，
        // 哪怕 RepositoryContext.repositoryName 与 worktree 末段不同，也要能正确剥离
        Path quirkyWorktree = Path.of("/tmp/worktrees/myalias");
        List<AgentRequest.RepositoryContext> repos = List.of(
                new AgentRequest.RepositoryContext(
                        1L, "repo-a", 10L, "main", "aaa111",
                        quirkyWorktree.toString(), null, "backend", true
                )
        );
        CodeGraphWorkspaceContext ctx = new CodeGraphWorkspaceContext(SPACE_ROOT, repos);

        assertThat(ctx.normalizeRepoFilePath(null, "myalias/src/Foo.java"))
                .isEqualTo("src/Foo.java");
    }

    @Test
    void normalizeRepoFilePath_passesThroughWhenNoPrefixMatches() {
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        assertThat(ctx.normalizeRepoFilePath(null, "src/Foo.java"))
                .isEqualTo("src/Foo.java");
    }

    @Test
    void normalizeRepoFilePath_handlesNullAndBlank() {
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        assertThat(ctx.normalizeRepoFilePath(null, null)).isNull();
        assertThat(ctx.normalizeRepoFilePath(null, "  ")).isEqualTo("  ");
    }

    @Test
    void normalizeRepoFilePath_doesNotStripWhenSegmentEqualsPath() {
        // 边界：filePath 恰好等于 alias，不应剥成空字符串（这种情况通常说明 LLM 在引用整个仓库根）
        CodeGraphWorkspaceContext ctx = projectSpaceContext();

        assertThat(ctx.normalizeRepoFilePath("repo-a", "repo-a")).isEqualTo("repo-a");
    }

    private CodeGraphWorkspaceContext projectSpaceContext() {
        List<AgentRequest.RepositoryContext> repos = List.of(
                new AgentRequest.RepositoryContext(
                        1L, "repo-a", 10L, "main", "aaa111",
                        REPO_A_WORKTREE.toString(), null, "backend", true
                ),
                new AgentRequest.RepositoryContext(
                        2L, "repo-b", 20L, "develop", "bbb222",
                        REPO_B_WORKTREE.toString(), null, "frontend", false
                )
        );
        return new CodeGraphWorkspaceContext(SPACE_ROOT, repos);
    }
}
