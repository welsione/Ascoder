package cn.welsione.ascoder.repository;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.MockExternalDependencies;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.git.GitBranchInfo;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RepositoryBranchService 集成测试：验证分支发现结果的刷新、去重、增量更新与查询。
 *
 * <p>通过 {@link MockExternalDependencies} mock {@link GitRepositoryService}，
 * 使 refresh 不触发真实 git CLI，聚焦 DB 存储与去重逻辑。</p>
 *
 * <p>{@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。
 * RepositoryBranchService 的 refresh/list 均为同步 {@code @Transactional} 方法，
 * 不涉及异步任务，因此无需 {@code @AfterEach} 清理。</p>
 */
@Import(MockExternalDependencies.class)
@Transactional
class RepositoryBranchServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private RepositoryBranchService service;

    @Autowired
    private CodeRepositoryJpaRepository codeRepositoryRepository;

    @Autowired
    private RepositoryBranchJpaRepository branchRepository;

    @Autowired
    private IntegrationTestDataFactory factory;

    @Autowired
    private GitRepositoryService gitRepositoryService;

    @BeforeEach
    void setUp() {
        Mockito.reset(gitRepositoryService);
    }

    @Test
    void refreshStoresBranchesFromGit() {
        CodeRepository repo = factory.createRepository("it-branch-refresh-" + System.nanoTime(), "local-path");

        when(gitRepositoryService.listBranches(any())).thenReturn(List.of(
                new GitBranchInfo("main", "refs/heads/main", "abc123def456",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD),
                new GitBranchInfo("develop", "refs/heads/develop", "def789ghi012",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD)
        ));
        when(gitRepositoryService.listRemoteHeads(any())).thenReturn(List.of());

        List<RepositoryBranch> branches = service.refresh(repo.getId());

        assertEquals(2, branches.size());
        assertTrue(branches.stream().anyMatch(b -> b.getName().equals("main")));
        assertTrue(branches.stream().anyMatch(b -> b.getName().equals("develop")));
        assertTrue(branches.stream().allMatch(RepositoryBranch::isActive));

        RepositoryBranch main = branches.stream().filter(b -> b.getName().equals("main")).findFirst().orElseThrow();
        assertEquals("abc123def456", main.getCommitSha());
        assertEquals("refs/heads/main", main.getRefName());
        assertNotNull(main.getLastSeenAt());
    }

    @Test
    void refreshDeduplicatesByRefNameCaseInsensitive() {
        CodeRepository repo = factory.createRepository("it-branch-dedup-" + System.nanoTime(), "local-path");

        // 同名分支（大小写不同 refName）应去重为一条
        when(gitRepositoryService.listBranches(any())).thenReturn(List.of(
                new GitBranchInfo("main", "refs/heads/main", "sha1",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD),
                new GitBranchInfo("main", "refs/heads/Main", "sha2",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD)
        ));
        when(gitRepositoryService.listRemoteHeads(any())).thenReturn(List.of());

        List<RepositoryBranch> branches = service.refresh(repo.getId());

        assertEquals(1, branches.size(), "refName 大小写不敏感去重后应仅一条");
    }

    @Test
    void refreshPrefersRemoteHeadOverLocalHead() {
        CodeRepository repo = factory.createRepository("it-branch-priority-" + System.nanoTime(), "local-path");
        repo.setRemoteUrl("https://github.com/test/repo.git");
        codeRepositoryRepository.save(repo);

        when(gitRepositoryService.listRemoteHeads(any())).thenReturn(List.of(
                new GitBranchInfo("main", "refs/heads/main", "remote-sha",
                        "origin", RepositoryBranchSourceKind.REMOTE_HEAD)
        ));
        when(gitRepositoryService.listBranches(any())).thenReturn(List.of(
                new GitBranchInfo("main", "refs/heads/main", "local-sha",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD)
        ));
        // remote repo 会触发 fetch
        Mockito.doNothing().when(gitRepositoryService).fetch(any());

        List<RepositoryBranch> branches = service.refresh(repo.getId());

        assertEquals(1, branches.size());
        RepositoryBranch branch = branches.get(0);
        // REMOTE_HEAD 优先级高于 LOCAL_HEAD，应保留 remote-sha
        assertEquals("remote-sha", branch.getCommitSha());
        assertEquals(RepositoryBranchSourceKind.REMOTE_HEAD, branch.getSourceKind());
        // 远程仓库应设置 lastPulledAt
        CodeRepository afterRefresh = codeRepositoryRepository.findById(repo.getId()).orElseThrow();
        assertNotNull(afterRefresh.getLastPulledAt());
    }

    @Test
    void refreshIncrementalUpdateAddsNewAndDeactivatesRemoved() {
        CodeRepository repo = factory.createRepository("it-branch-incremental-" + System.nanoTime(), "local-path");

        // 第一次刷新：main + develop
        when(gitRepositoryService.listBranches(any())).thenReturn(List.of(
                new GitBranchInfo("main", "refs/heads/main", "sha1",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD),
                new GitBranchInfo("develop", "refs/heads/develop", "sha2",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD)
        ));
        when(gitRepositoryService.listRemoteHeads(any())).thenReturn(List.of());

        service.refresh(repo.getId());
        assertEquals(2, branchRepository.findByRepository_IdAndActiveTrueOrderByNameAscSourceKindAsc(repo.getId()).size());

        // 第二次刷新：main + feature（develop 消失，feature 新增）
        Mockito.reset(gitRepositoryService);
        when(gitRepositoryService.listBranches(any())).thenReturn(List.of(
                new GitBranchInfo("main", "refs/heads/main", "sha1-updated",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD),
                new GitBranchInfo("feature", "refs/heads/feature", "sha3",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD)
        ));
        when(gitRepositoryService.listRemoteHeads(any())).thenReturn(List.of());

        List<RepositoryBranch> afterSecond = service.refresh(repo.getId());

        // 活跃分支：main + feature（develop 应被停用）
        List<RepositoryBranch> active = afterSecond.stream().filter(RepositoryBranch::isActive).toList();
        assertEquals(2, active.size());
        assertTrue(active.stream().anyMatch(b -> b.getName().equals("main")));
        assertTrue(active.stream().anyMatch(b -> b.getName().equals("feature")));

        // develop 应被停用
        List<RepositoryBranch> all = branchRepository.findByRepository_IdOrderByNameAscSourceKindAsc(repo.getId());
        RepositoryBranch develop = all.stream().filter(b -> b.getName().equals("develop")).findFirst().orElseThrow();
        assertFalse(develop.isActive());

        // main 的 commitSha 应更新
        RepositoryBranch main = active.stream().filter(b -> b.getName().equals("main")).findFirst().orElseThrow();
        assertEquals("sha1-updated", main.getCommitSha());
    }

    @Test
    void refreshThrowsWhenNoBranchesDiscovered() {
        CodeRepository repo = factory.createRepository("it-branch-empty-" + System.nanoTime(), "local-path");

        when(gitRepositoryService.listBranches(any())).thenReturn(List.of());
        when(gitRepositoryService.listRemoteHeads(any())).thenReturn(List.of());

        assertThrows(ValidationException.class, () -> service.refresh(repo.getId()));
    }

    @Test
    void listReturnsActiveBranchesOnly() {
        CodeRepository repo = factory.createRepository("it-branch-list-" + System.nanoTime(), "local-path");
        factory.createRepositoryBranch(repo, "main", "refs/heads/main", "sha1");
        RepositoryBranch inactive = factory.createRepositoryBranch(repo, "old", "refs/heads/old", "sha2");
        inactive.deactivate();
        branchRepository.save(inactive);

        List<RepositoryBranch> active = service.list(repo.getId());

        assertEquals(1, active.size());
        assertEquals("main", active.get(0).getName());
    }

    @Test
    void listThrowsWhenRepositoryNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.list(999999L));
    }

    @Test
    void getActiveEntityReturnsBranch() {
        CodeRepository repo = factory.createRepository("it-branch-get-" + System.nanoTime(), "local-path");
        RepositoryBranch branch = factory.createRepositoryBranch(repo, "main", "refs/heads/main", "sha1");

        RepositoryBranch found = service.getActiveEntity(branch.getId(), repo.getId());

        assertEquals(branch.getId(), found.getId());
        assertTrue(found.isActive());
    }

    @Test
    void getActiveEntityThrowsWhenBranchNotActive() {
        CodeRepository repo = factory.createRepository("it-branch-inactive-" + System.nanoTime(), "local-path");
        RepositoryBranch branch = factory.createRepositoryBranch(repo, "old", "refs/heads/old", "sha1");
        branch.deactivate();
        branchRepository.save(branch);

        assertThrows(ValidationException.class, () -> service.getActiveEntity(branch.getId(), repo.getId()));
    }

    @Test
    void getActiveEntityThrowsWhenBranchBelongsToDifferentRepo() {
        CodeRepository repoA = factory.createRepository("it-branch-repo-a-" + System.nanoTime(), "path-a");
        CodeRepository repoB = factory.createRepository("it-branch-repo-b-" + System.nanoTime(), "path-b");
        RepositoryBranch branch = factory.createRepositoryBranch(repoA, "main", "refs/heads/main", "sha1");

        assertThrows(ValidationException.class, () -> service.getActiveEntity(branch.getId(), repoB.getId()));
    }
}
