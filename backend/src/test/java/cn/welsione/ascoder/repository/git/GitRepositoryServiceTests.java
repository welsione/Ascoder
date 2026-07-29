package cn.welsione.ascoder.repository.git;

import cn.welsione.ascoder.common.CommandResult;
import cn.welsione.ascoder.runtime.application.RuntimeSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Git 仓库服务对分支引用解析和远端同步的处理。
 */
class GitRepositoryServiceTests {

    private final GitCommandRunner commandRunner = mock(GitCommandRunner.class);
    private final RuntimeSettingsService runtimeSettings = mock(RuntimeSettingsService.class);
    private final GitRepositoryService service = new GitRepositoryService(commandRunner, runtimeSettings);
    private final Path repositoryPath = Path.of("/tmp/repo");

    @Test
    void commitShaFetchesAndRetriesWhenRemoteBranchIsMissingLocally() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'origin/hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(true, "abc123\n")
                );
        when(commandRunner.runAsync(any(), any(), any(), any()))
                .thenReturn(new CommandResult(true, ""));

        String commitSha = service.commitSha(repositoryPath, "hotfix/5.6.x");

        assertThat(commitSha).isEqualTo("abc123");
        ArgumentCaptor<List<String>> runCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(4)).run(runCaptor.capture(), any(), any());
        assertThat(runCaptor.getAllValues())
                .extracting(command -> command.get(command.size() - 1))
                .containsExactly(
                        "hotfix/5.6.x",
                        "origin/hotfix/5.6.x",
                        "hotfix/5.6.x",
                        "origin/hotfix/5.6.x"
                );
        ArgumentCaptor<List<String>> asyncCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(1)).runAsync(asyncCaptor.capture(), any(), any(), any());
        assertThat(asyncCaptor.getValue()).containsSequence("fetch", "--all", "--prune");
    }

    @Test
    void commitShaDoesNotPrefixOriginTwice() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new CommandResult(true, "def456\n"));

        String commitSha = service.commitSha(repositoryPath, "origin/hotfix/5.6.x");

        assertThat(commitSha).isEqualTo("def456");
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(1)).run(captor.capture(), any(), any());
        assertThat(captor.getValue()).containsSequence("rev-parse", "origin/hotfix/5.6.x");
    }

    @Test
    void commitShaFetchesTargetBranchWhenRepositoryRefspecIsNarrow() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(
                        new CommandResult(false, "fatal: ambiguous argument 'origin/hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'origin/hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(true, ""),
                        new CommandResult(true, "fedcba\n")
                );
        when(commandRunner.runAsync(any(), any(), any(), any()))
                .thenReturn(new CommandResult(true, ""));

        String commitSha = service.commitSha(repositoryPath, "origin/hotfix/5.6.x");

        assertThat(commitSha).isEqualTo("fedcba");
        ArgumentCaptor<List<String>> runCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(6)).run(runCaptor.capture(), any(), any());
        assertThat(runCaptor.getAllValues().get(4))
                .containsSequence(
                        "fetch",
                        "origin",
                        "+refs/heads/hotfix/5.6.x:refs/remotes/origin/hotfix/5.6.x",
                        "--prune"
                );
        ArgumentCaptor<List<String>> asyncCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(1)).runAsync(asyncCaptor.capture(), any(), any(), any());
        assertThat(asyncCaptor.getValue()).containsSequence("fetch", "--all", "--prune");
    }

    @Test
    void commitShaFetchesNestedRemoteTrackingBranchFromLocalCloneRemote() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(
                        new CommandResult(false, "fatal: ambiguous argument 'origin/hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'origin/hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: couldn't find remote ref refs/heads/hotfix/5.6.x"),
                        new CommandResult(true, ""),
                        new CommandResult(true, "fedcba\n")
                );
        when(commandRunner.runAsync(any(), any(), any(), any()))
                .thenReturn(new CommandResult(true, ""));

        String commitSha = service.commitSha(repositoryPath, "origin/hotfix/5.6.x");

        assertThat(commitSha).isEqualTo("fedcba");
        ArgumentCaptor<List<String>> runCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(7)).run(runCaptor.capture(), any(), any());
        assertThat(runCaptor.getAllValues().get(5))
                .containsSequence(
                        "fetch",
                        "origin",
                        "+refs/remotes/origin/hotfix/5.6.x:refs/remotes/origin/hotfix/5.6.x",
                        "--prune"
                );
        ArgumentCaptor<List<String>> asyncCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(1)).runAsync(asyncCaptor.capture(), any(), any(), any());
        assertThat(asyncCaptor.getValue()).containsSequence("fetch", "--all", "--prune");
    }

    @Test
    void gitDiff_throwsOnFailureInsteadOfReturningStderr() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(new CommandResult(false, "fatal: bad revision 'abc..def'"));

        assertThatThrownBy(() -> service.gitDiff(repositoryPath, "abc", "def", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("读取差异失败");
    }
}
