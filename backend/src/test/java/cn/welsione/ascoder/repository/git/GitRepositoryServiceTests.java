package cn.welsione.ascoder.repository.git;

import cn.welsione.ascoder.common.CommandResult;
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
    private final GitRepositoryService service = new GitRepositoryService(commandRunner);
    private final Path repositoryPath = Path.of("/tmp/repo");

    @Test
    void commitShaFetchesAndRetriesWhenRemoteBranchIsMissingLocally() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'origin/hotfix/5.6.x'"),
                        new CommandResult(true, ""),
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(true, "abc123\n")
                );

        String commitSha = service.commitSha(repositoryPath, "hotfix/5.6.x");

        assertThat(commitSha).isEqualTo("abc123");
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(5)).run(captor.capture(), any(), any());
        assertThat(captor.getAllValues())
                .extracting(command -> command.get(command.size() - 1))
                .containsExactly(
                        "hotfix/5.6.x",
                        "origin/hotfix/5.6.x",
                        "--prune",
                        "hotfix/5.6.x",
                        "origin/hotfix/5.6.x"
                );
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
                        new CommandResult(true, ""),
                        new CommandResult(false, "fatal: ambiguous argument 'origin/hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(true, ""),
                        new CommandResult(true, "fedcba\n")
                );

        String commitSha = service.commitSha(repositoryPath, "origin/hotfix/5.6.x");

        assertThat(commitSha).isEqualTo("fedcba");
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(7)).run(captor.capture(), any(), any());
        assertThat(captor.getAllValues().get(5))
                .containsSequence(
                        "fetch",
                        "origin",
                        "+refs/heads/hotfix/5.6.x:refs/remotes/origin/hotfix/5.6.x",
                        "--prune"
                );
    }

    @Test
    void commitShaFetchesNestedRemoteTrackingBranchFromLocalCloneRemote() {
        when(commandRunner.run(any(), any(), any()))
                .thenReturn(
                        new CommandResult(false, "fatal: ambiguous argument 'origin/hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(true, ""),
                        new CommandResult(false, "fatal: ambiguous argument 'origin/hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: ambiguous argument 'hotfix/5.6.x'"),
                        new CommandResult(false, "fatal: couldn't find remote ref refs/heads/hotfix/5.6.x"),
                        new CommandResult(true, ""),
                        new CommandResult(true, "fedcba\n")
                );

        String commitSha = service.commitSha(repositoryPath, "origin/hotfix/5.6.x");

        assertThat(commitSha).isEqualTo("fedcba");
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(8)).run(captor.capture(), any(), any());
        assertThat(captor.getAllValues().get(6))
                .containsSequence(
                        "fetch",
                        "origin",
                        "+refs/remotes/origin/hotfix/5.6.x:refs/remotes/origin/hotfix/5.6.x",
                        "--prune"
                );
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
