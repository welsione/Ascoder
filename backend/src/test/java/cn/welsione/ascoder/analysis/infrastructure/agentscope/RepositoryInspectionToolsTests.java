package cn.welsione.ascoder.analysis.infrastructure.agentscope;

import cn.welsione.ascoder.analysis.CodeGraphWorkspaceContext;
import cn.welsione.ascoder.analysis.RepositoryInspectionTools;
import cn.welsione.ascoder.common.FilePathSanitizer;
import cn.welsione.ascoder.repository.git.GitCommandRunner;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryInspectionToolsTests {

    private final GitCommandRunner commandRunner = mock(GitCommandRunner.class);
    private final GitRepositoryService gitRepositoryService = new GitRepositoryService(commandRunner);
    private final Path repositoryPath = Path.of("/tmp/repo");
    private final AtomicReference<String> codeContext = new AtomicReference<>("");
    private final CodeGraphWorkspaceContext workspaceContext = CodeGraphWorkspaceContext.single(repositoryPath);
    private final FilePathSanitizer filePathSanitizer = new FilePathSanitizer();
    private final RepositoryInspectionTools tools = new RepositoryInspectionTools(
            gitRepositoryService, workspaceContext, filePathSanitizer, codeContext, "ProjectSpace: demo"
    );

    @Test
    void registerAllRegistersFiveGitToolMethods() {
        Toolkit toolkit = new Toolkit();
        tools.registerAll(toolkit);

        assertThat(toolkit.getToolNames())
                .containsExactlyInAnyOrder(
                        "git_list_branches",
                        "git_get_commit",
                        "git_show_log",
                        "git_diff",
                        "git_blame"
                );
    }

    @Test
    void listBranchesFormatsBranchInfoLines() {
        when(commandRunner.run(any(), any(), any()))
                .thenAnswer(invocation -> new cn.welsione.ascoder.common.CommandResult(true,
                        "main|aaa111\norigin/feature/x|bbb222\norigin/HEAD|ccc333\n"));

        ToolResultBlock result = tools.listBranches(null).block();

        String text = extractText(result);
        assertThat(text)
                .contains("- main aaa111")
                .contains("- feature/x bbb222")
                .doesNotContain("origin/HEAD")
                .doesNotContain("origin/feature/x");
    }

    @Test
    void getCommitRejectsBlankRef() {
        ToolResultBlock result = tools.getCommit("   ", null).block();
        assertThat(extractText(result)).contains("Error: git_get_commit: ref is required.");
    }

    @Test
    void getCommitRejectsIllegalCharacters() {
        ToolResultBlock result = tools.getCommit("main; rm -rf /", null).block();
        assertThat(extractText(result)).contains("Error: git_get_commit: ref must match");
    }

    @Test
    void getCommitDelegatesToService() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new cn.welsione.ascoder.common.CommandResult(true, "deadbeef\n"));

        ToolResultBlock result = tools.getCommit("main", null).block();

        assertThat(extractText(result)).isEqualTo("deadbeef");
    }

    @Test
    void showLogDefaultsToHeadWhenRefBlank() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new cn.welsione.ascoder.common.CommandResult(true, "abc123 init\n"));

        ToolResultBlock result = tools.showLog(null, null, null, null).block();

        assertThat(extractText(result)).contains("abc123 init");
    }

    @Test
    void showLogCapsMaxCountAt200() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new cn.welsione.ascoder.common.CommandResult(true, ""));

        tools.showLog("main", 500, null, null).block();

        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(1)).run(any(), any(), any());
    }

    @Test
    void showLogRejectsIllegalRef() {
        ToolResultBlock result = tools.showLog("main && curl evil", 20, null, null).block();
        assertThat(extractText(result)).contains("Error: git_show_log: ref must match");
    }

    @Test
    void showLogRejectsPathTraversal() {
        ToolResultBlock result = tools.showLog("main", 20, "../etc/passwd", null).block();
        assertThat(extractText(result)).contains("Error: git_show_log: filePath must be a relative path");
        verify(commandRunner, never()).run(any(), any(), any());
    }

    @Test
    void diffRejectsIllegalRefs() {
        ToolResultBlock result = tools.diff("main", "head; cat /etc/passwd", null, null).block();
        assertThat(extractText(result)).contains("Error: git_diff: baseRef and headRef must match");
    }

    @Test
    void diffRejectsPathTraversal() {
        ToolResultBlock result = tools.diff("main", "feature", "../secret.txt", null).block();
        assertThat(extractText(result)).contains("Error: git_diff: filePath must be a relative path");
        verify(commandRunner, never()).run(any(), any(), any());
    }

    @Test
    void diffDelegatesToServiceWithPathspec() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new cn.welsione.ascoder.common.CommandResult(true, "diff output"));

        ToolResultBlock result = tools.diff("main", "feature/x", "src/main/Foo.java", null).block();

        assertThat(extractText(result)).isEqualTo("diff output");
    }

    @Test
    void blameRejectsPathTraversal() {
        ToolResultBlock result = tools.blame("../etc/passwd", 1, 5, null).block();
        assertThat(extractText(result)).contains("Error: git_blame: filePath must be a relative path");
    }

    @Test
    void blameDelegatesToService() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new cn.welsione.ascoder.common.CommandResult(true,
                        "abc123 (Alice 2026-01-01) line 1\ndef456 (Bob 2026-01-02) line 2\n"));

        ToolResultBlock result = tools.blame("src/main/Foo.java", 1, 2, null).block();

        String text = extractText(result);
        assertThat(text)
                .contains("abc123 (Alice 2026-01-01) line 1")
                .contains("def456 (Bob 2026-01-02) line 2");
    }

    @Test
    void blameStripsRepositoryNamePrefix() {
        // workspaceContext 是 single(/tmp/repo)，末段 "repo" 即可作为前缀剥离
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new cn.welsione.ascoder.common.CommandResult(true, "abc123 line\n"));

        tools.blame("repo/src/main/Foo.java", 1, 2, null).block();

        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(captor.capture(), any(), any());
        assertThat(captor.getValue()).containsSequence("--", "src/main/Foo.java");
    }

    @Test
    void showLogStripsRepositoryNamePrefixFromFilePath() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new cn.welsione.ascoder.common.CommandResult(true, "abc123 init\n"));

        tools.showLog("main", 10, "repo/src/Foo.java", null).block();

        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(captor.capture(), any(), any());
        assertThat(captor.getValue()).containsSequence("--", "src/Foo.java");
    }

    @Test
    void diffStripsRepositoryNamePrefixFromFilePath() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new cn.welsione.ascoder.common.CommandResult(true, "diff output"));

        tools.diff("main", "feature/x", "repo/src/main/Foo.java", null).block();

        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(captor.capture(), any(), any());
        assertThat(captor.getValue()).containsSequence("--", "src/main/Foo.java");
    }

    @Test
    void duplicateCallWithinSameToolIsIntercepted() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new cn.welsione.ascoder.common.CommandResult(true, "main|aaa\n"));

        tools.listBranches(null).block();
        tools.listBranches(null).block();

        verify(commandRunner, times(1)).run(any(), any(), any());
    }

    @Test
    void codeContextAccumulatesAcrossTools() {
        when(commandRunner.run(any(), any(), any()))
                .thenAnswer(invocation -> new cn.welsione.ascoder.common.CommandResult(true, "main|aaa\n"));
        var branches1 = tools.listBranches(null).block();
        var branches2 = tools.listBranches(null).block();

        assertThat(extractText(branches1)).contains("- main aaa");
        assertThat(extractText(branches2)).contains("Duplicate call intercepted");
        assertThat(codeContext.get()).contains("git_list_branches");
    }

    @Test
    void sanitizeRefAcceptsCommonGitRefShapes() {
        assertThat(invokeSanitize("main")).isEqualTo("main");
        assertThat(invokeSanitize("origin/main")).isEqualTo("origin/main");
        assertThat(invokeSanitize("feature/foo_bar-v1.0")).isEqualTo("feature/foo_bar-v1.0");
        assertThat(invokeSanitize("HEAD~1")).isEqualTo("HEAD~1");
        assertThat(invokeSanitize("v1.0.0")).isEqualTo("v1.0.0");
    }

    @Test
    void sanitizeRefRejectsInjectionAttempts() {
        assertThat(invokeSanitize("main; ls")).isNull();
        assertThat(invokeSanitize("main && rm")).isNull();
        assertThat(invokeSanitize("main|grep")).isNull();
        assertThat(invokeSanitize("main$HOME")).isNull();
        assertThat(invokeSanitize("main\nreboot")).isNull();
        assertThat(invokeSanitize("main`whoami`")).isNull();
        assertThat(invokeSanitize("")).isNull();
        assertThat(invokeSanitize(" ".repeat(201))).isNull();
    }

    private String invokeSanitize(String ref) {
        try {
            java.lang.reflect.Method method = RepositoryInspectionTools.class.getDeclaredMethod("sanitizeRef", String.class);
            method.setAccessible(true);
            return (String) method.invoke(tools, ref);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
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
