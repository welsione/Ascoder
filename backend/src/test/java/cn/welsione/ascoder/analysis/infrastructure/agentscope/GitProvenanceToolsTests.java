package cn.welsione.ascoder.analysis.infrastructure.agentscope;

import cn.welsione.ascoder.analysis.CodeGraphWorkspaceContext;
import cn.welsione.ascoder.analysis.GitProvenanceTools;
import cn.welsione.ascoder.common.CommandResult;
import cn.welsione.ascoder.runtime.application.RuntimeSettingsService;
import cn.welsione.ascoder.repository.git.GitCommandRunner;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Git 证据追溯工具的注册、安全校验和 Git 命令委派行为。
 */
class GitProvenanceToolsTests {

    private final GitCommandRunner commandRunner = mock(GitCommandRunner.class);
    private final RuntimeSettingsService runtimeSettings = mock(RuntimeSettingsService.class);
    private final GitRepositoryService gitRepositoryService = new GitRepositoryService(commandRunner, runtimeSettings);
    private final Path repositoryPath = Path.of("/tmp/repo");
    private final AtomicReference<String> codeContext = new AtomicReference<>("");
    private final CodeGraphWorkspaceContext workspaceContext = CodeGraphWorkspaceContext.single(repositoryPath);
    private final GitProvenanceTools tools = new GitProvenanceTools(
            gitRepositoryService, workspaceContext, codeContext, "ProjectSpace: demo"
    );

    @Test
    void registerAllRegistersFiveProvenanceToolMethods() {
        Toolkit toolkit = new Toolkit();
        tools.registerAll(toolkit);

        assertThat(toolkit.getToolNames())
                .containsExactlyInAnyOrder(
                        "git_recent_commit",
                        "git_blame_range",
                        "git_commit_detail",
                        "git_file_history",
                        "git_diff_for_commit"
                );
    }

    @Test
    void recentCommitDelegatesWithPathspec() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new CommandResult(true, "commit deadbeef\nauthor Alice <alice@example.com>\n"));

        ToolResultBlock result = tools.recentCommit("src/main/Foo.java", null).block();

        assertThat(extractText(result)).contains("Alice <alice@example.com>");
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(captor.capture(), any(), any());
        assertThat(captor.getValue()).containsSequence("--", "src/main/Foo.java");
    }

    @Test
    void blameRangeRejectsInvalidRangeBeforeGit() {
        ToolResultBlock result = tools.blameRange("src/main/Foo.java", 8, 3, null).block();

        assertThat(extractText(result)).contains("Error: git_blame_range: startLine and endLine must be >= 1");
        verify(commandRunner, never()).run(any(), any(), any());
    }

    @Test
    void blameRangeRejectsPathTraversalBeforeGit() {
        ToolResultBlock result = tools.blameRange("../secret.txt", 1, 2, null).block();

        assertThat(extractText(result)).contains("Error: git_blame_range: filePath must be a relative path");
        verify(commandRunner, never()).run(any(), any(), any());
    }

    @Test
    void commitDetailRejectsUnsafeRefBeforeGit() {
        ToolResultBlock result = tools.commitDetail("main; rm -rf /", null).block();

        assertThat(extractText(result)).contains("Error: git_commit_detail: commitSha must match");
        verify(commandRunner, never()).run(any(), any(), any());
    }

    @Test
    void fileHistoryCapsMaxCountAtServiceBoundary() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new CommandResult(true, "abc123 | 2026-01-01T00:00:00Z | Bob <bob@example.com> | init\n"));

        ToolResultBlock result = tools.fileHistory("README.md", 500, null).block();

        assertThat(extractText(result)).contains("Bob <bob@example.com>");
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(captor.capture(), any(), any());
        assertThat(captor.getValue()).containsSequence("-n", "100");
    }

    @Test
    void diffForCommitDefaultsToStatOnly() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new CommandResult(true, "README.md | 2 +-\n"));

        ToolResultBlock result = tools.diffForCommit("deadbeef", "README.md", null, null).block();

        assertThat(extractText(result)).contains("README.md");
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(captor.capture(), any(), any());
        assertThat(captor.getValue()).contains("--stat");
        assertThat(captor.getValue()).containsSequence("--", "README.md");
    }

    @Test
    void duplicateCallIsInterceptedAndContextAccumulates() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new CommandResult(true, "commit deadbeef\n"));

        ToolResultBlock first = tools.commitDetail("deadbeef", null).block();
        ToolResultBlock second = tools.commitDetail("deadbeef", null).block();

        assertThat(extractText(first)).contains("deadbeef");
        assertThat(extractText(second)).contains("Duplicate call intercepted");
        assertThat(codeContext.get()).contains("git_commit_detail").contains("ProjectSpace: demo");
        verify(commandRunner).run(any(), any(), any());
    }

    @Test
    void recentCommitStripsRepositoryNamePrefixFromFilePath() {
        // LLM 在多仓库工作空间里看到 "path=/tmp/repo"（末段 repo），
        // 把 alias 当目录前缀传入：repo/src/Foo.java，应剥离成 src/Foo.java
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new CommandResult(true, "commit deadbeef\n"));

        tools.recentCommit("repo/src/Foo.java", null).block();

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(captor.capture(), any(), any());
        assertThat(captor.getValue()).containsSequence("--", "src/Foo.java");
        assertThat(captor.getValue()).doesNotContain("repo/src/Foo.java");
    }

    @Test
    void fileHistoryStripsRepositoryNamePrefixFromFilePath() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new CommandResult(true, "abc123 | 2026-01-01 | Bob | init\n"));

        tools.fileHistory("repo/README.md", 10, null).block();

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(captor.capture(), any(), any());
        assertThat(captor.getValue()).containsSequence("--", "README.md");
    }

    @Test
    void blameRangeStripsRepositoryNamePrefixFromFilePath() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new CommandResult(true, "abc123 (Alice 2026-01-01) line1\n"));

        tools.blameRange("repo/src/Foo.java", 1, 5, null).block();

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(captor.capture(), any(), any());
        assertThat(captor.getValue()).containsSequence("--", "src/Foo.java");
    }

    private String extractText(ToolResultBlock block) {
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
