package cn.welsione.ascoder.repository;

import cn.welsione.ascoder.repository.git.GitBranchInfo;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证仓库分支发现结果的合并和状态维护。
 */
class RepositoryBranchServiceTests {

    private final RepositoryBranchJpaRepository repository = mock(RepositoryBranchJpaRepository.class);
    private final CodeRepositoryJpaRepository codeRepositoryJpaRepository = mock(CodeRepositoryJpaRepository.class);
    private final GitRepositoryService gitRepositoryService = mock(GitRepositoryService.class);
    private final RepositoryBranchService service = new RepositoryBranchService(
            repository,
            codeRepositoryJpaRepository,
            gitRepositoryService
    );

    @Test
    void refreshKeepsOnlyPreferredRefForSameBranchName() {
        CodeRepository codeRepository = new CodeRepository();
        codeRepository.setId(1L);
        codeRepository.setName("qys-private");
        codeRepository.setLocalPath("/tmp/qys-private");
        codeRepository.setRemoteUrl("https://example.test/qys-private.git");

        RepositoryBranch remoteHead = branch(
                codeRepository,
                "AHJT",
                "refs/heads/AHJT",
                RepositoryBranchSourceKind.REMOTE_HEAD
        );
        RepositoryBranch remoteTracking = branch(
                codeRepository,
                "AHJT",
                "refs/remotes/origin/AHJT",
                RepositoryBranchSourceKind.REMOTE_TRACKING
        );

        GitBranchInfo remoteHeadInfo = new GitBranchInfo(
                "AHJT",
                "refs/heads/AHJT",
                "7baa58b889734a39e",
                "origin",
                RepositoryBranchSourceKind.REMOTE_HEAD
        );
        GitBranchInfo remoteTrackingInfo = new GitBranchInfo(
                "AHJT",
                "refs/remotes/origin/AHJT",
                "7baa58b889734a39e",
                "origin",
                RepositoryBranchSourceKind.REMOTE_TRACKING
        );

        when(codeRepositoryJpaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(codeRepository));
        when(gitRepositoryService.listRemoteHeads(Path.of("/tmp/qys-private"))).thenReturn(List.of(remoteHeadInfo));
        when(gitRepositoryService.listBranches(Path.of("/tmp/qys-private"))).thenReturn(List.of(remoteTrackingInfo));
        when(repository.findByRepository_IdOrderByNameAscSourceKindAsc(1L))
                .thenReturn(List.of(remoteHead, remoteTracking));
        when(repository.findByRepository_IdAndActiveTrueOrderByNameAscSourceKindAsc(1L))
                .thenReturn(List.of(remoteHead));

        List<RepositoryBranch> branches = service.refresh(1L);

        assertThat(branches).containsExactly(remoteHead);
        assertThat(remoteHead.isActive()).isTrue();
        assertThat(remoteTracking.isActive()).isFalse();
        ArgumentCaptor<Iterable<RepositoryBranch>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).contains(remoteHead, remoteTracking);
    }

    private RepositoryBranch branch(CodeRepository codeRepository,
                                    String name,
                                    String refName,
                                    RepositoryBranchSourceKind sourceKind) {
        RepositoryBranch branch = new RepositoryBranch();
        branch.setRepository(codeRepository);
        branch.updateFrom(name, refName, "7baa58b889734a39e", "origin", sourceKind, new Date());
        return branch;
    }
}
