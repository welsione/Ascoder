package cn.welsione.ascoder.repository.workspace;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.MockExternalDependencies;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.RepositoryStatus;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * BranchWorkspaceService 集成测试：验证 worktree 准备、索引、查询与状态流转。
 *
 * <p>通过 {@link MockExternalDependencies} mock {@link GitRepositoryService} 和 {@link CodeGraphClient}，
 * 使 prepare/index 不触发真实 git CLI / codegraph CLI，聚焦状态流转与 DB 存储。</p>
 *
 * <p>BranchWorkspaceService 的 prepare/index 均为同步 {@code @Transactional} 方法（不经过 TaskEngine），
 * 因此使用 {@code @Transactional} 保证测试后自动回滚。</p>
 */
@Import(MockExternalDependencies.class)
@Transactional
class BranchWorkspaceServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private BranchWorkspaceService service;

    @Autowired
    private BranchWorkspaceJpaRepository workspaceRepository;

    @Autowired
    private CodeRepositoryJpaRepository codeRepositoryRepository;

    @Autowired
    private IntegrationTestDataFactory factory;

    @Autowired
    private GitRepositoryService gitRepositoryService;

    @Autowired
    private CodeGraphClient codeGraphClient;

    @BeforeEach
    void setUp() {
        Mockito.reset(gitRepositoryService, codeGraphClient);
        stubGitBasics();
    }

    /**
     * Stub git 基础查询方法，使 prepare/index 不抛异常。
     */
    private void stubGitBasics() {
        when(gitRepositoryService.commitSha(any(), anyString())).thenReturn("abc123def456");
        when(gitRepositoryService.commitMessage(any(), anyString())).thenReturn("测试提交");
        Mockito.doNothing().when(gitRepositoryService)
                .createOrUpdateDetachedWorktree(any(), anyString(), anyString(), any());
    }

    @Test
    void prepareCreatesWorkspaceWithReadyStatus() {
        CodeRepository repo = createLocalRepository();

        BranchWorkspace workspace = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));

        assertNotNull(workspace.getId());
        assertEquals(BranchWorkspaceStatus.READY, workspace.getStatus());
        assertEquals("main", workspace.getBranchName());
        assertEquals("abc123def456", workspace.getCommitSha());
        assertEquals("测试提交", workspace.getCommitMessage());
        assertNotNull(workspace.getWorktreePath());
        assertNotNull(workspace.getCodegraphIndexPath());

        BranchWorkspace persisted = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertEquals(BranchWorkspaceStatus.READY, persisted.getStatus());
    }

    @Test
    void prepareWithSelectedCommitShaUsesProvidedSha() {
        CodeRepository repo = createLocalRepository();

        when(gitRepositoryService.commitMessage(any(), eq("customsha123"))).thenReturn("自定义提交");

        BranchWorkspace workspace = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("feature"), "customsha123");

        assertEquals(BranchWorkspaceStatus.READY, workspace.getStatus());
        assertEquals("customsha123", workspace.getCommitSha());
        assertEquals("自定义提交", workspace.getCommitMessage());
    }

    @Test
    void prepareUpdatesExistingWorkspaceOnReprepare() {
        CodeRepository repo = createLocalRepository();

        BranchWorkspace first = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));
        assertEquals(BranchWorkspaceStatus.READY, first.getStatus());

        // 同分支再次 prepare 应复用已有 workspace（不会创建重复记录）
        BranchWorkspace second = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));

        assertEquals(first.getId(), second.getId());
        assertEquals(BranchWorkspaceStatus.READY, second.getStatus());
    }

    @Test
    void prepareFailsWhenGitThrowsException() {
        CodeRepository repo = createLocalRepository();

        Mockito.doThrow(new RuntimeException("git worktree add 失败"))
                .when(gitRepositoryService)
                .createOrUpdateDetachedWorktree(any(), anyString(), anyString(), any());

        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.prepare(repo.getId(), new CreateBranchWorkspaceRequest("main")));

        assertTrue(ex.getMessage().contains("git worktree add 失败"));

        // workspace 应存在但状态为 FAILED
        List<BranchWorkspace> workspaces = workspaceRepository.findByRepository_IdOrderByBranchNameAsc(repo.getId());
        assertEquals(1, workspaces.size());
        BranchWorkspace workspace = workspaces.get(0);
        assertEquals(BranchWorkspaceStatus.FAILED, workspace.getStatus());
        assertNotNull(workspace.getLastIndexError());
        assertTrue(workspace.getLastIndexError().contains("git worktree add 失败"));
    }

    @Test
    void indexTransitionsToIndexedOnSuccess() {
        CodeRepository repo = createLocalRepository();
        BranchWorkspace workspace = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));

        when(codeGraphClient.index(any(Path.class), any(Path.class), any()))
                .thenReturn(CodeGraphToolResult.success("索引完成"));

        BranchWorkspace indexed = service.index(workspace.getId());

        assertEquals(BranchWorkspaceStatus.READY, indexed.getStatus());
        assertNotNull(indexed.getLastIndexedAt());
        assertEquals("abc123def456", indexed.getCommitSha());
    }

    @Test
    void indexTransitionsToFailedOnCodeGraphError() {
        CodeRepository repo = createLocalRepository();
        BranchWorkspace workspace = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));

        when(codeGraphClient.index(any(Path.class), any(Path.class), any()))
                .thenReturn(CodeGraphToolResult.error("CodeGraph 索引失败：解析错误"));

        BranchWorkspace failed = service.index(workspace.getId());

        assertEquals(BranchWorkspaceStatus.FAILED, failed.getStatus());
        assertNotNull(failed.getLastIndexError());
        assertTrue(failed.getLastIndexError().contains("解析错误"));
    }

    @Test
    void indexThrowsWhenGitThrowsException() {
        CodeRepository repo = createLocalRepository();
        BranchWorkspace workspace = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));

        Mockito.reset(gitRepositoryService);
        when(gitRepositoryService.commitSha(any(), anyString())).thenThrow(new RuntimeException("git rev-parse 失败"));

        assertThrows(RuntimeException.class, () -> service.index(workspace.getId()));

        BranchWorkspace failed = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertEquals(BranchWorkspaceStatus.FAILED, failed.getStatus());
    }

    @Test
    void indexThrowsWhenAlreadyIndexing() {
        CodeRepository repo = createLocalRepository();
        BranchWorkspace workspace = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));

        // 手动设为 INDEXING 状态模拟正在索引
        workspace.indexing();
        workspaceRepository.save(workspace);

        cn.welsione.ascoder.common.exception.InvalidStateException ex =
                assertThrows(cn.welsione.ascoder.common.exception.InvalidStateException.class,
                        () -> service.index(workspace.getId()));
        assertTrue(ex.getMessage().contains("正在索引中"));
    }

    @Test
    void listReturnsWorkspacesByRepository() {
        CodeRepository repo = createLocalRepository();

        service.prepare(repo.getId(), new CreateBranchWorkspaceRequest("main"));
        service.prepare(repo.getId(), new CreateBranchWorkspaceRequest("develop"));

        List<BranchWorkspace> workspaces = service.list(repo.getId());

        assertEquals(2, workspaces.size());
        assertTrue(workspaces.stream().anyMatch(w -> w.getBranchName().equals("main")));
        assertTrue(workspaces.stream().anyMatch(w -> w.getBranchName().equals("develop")));
    }

    @Test
    void getReturnsWorkspaceById() {
        CodeRepository repo = createLocalRepository();
        BranchWorkspace created = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));

        BranchWorkspace found = service.get(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals("main", found.getBranchName());
    }

    @Test
    void getThrowsWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(999999L));
    }

    @Test
    void getReadyEntityThrowsWhenNotReady() {
        CodeRepository repo = createLocalRepository();
        BranchWorkspace workspace = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));

        // 手动设为 FAILED 状态
        workspace.fail("测试失败");
        workspaceRepository.save(workspace);

        cn.welsione.ascoder.common.exception.InvalidStateException ex =
                assertThrows(cn.welsione.ascoder.common.exception.InvalidStateException.class,
                        () -> service.getReadyEntity(workspace.getId(), repo.getId()));
        assertTrue(ex.getMessage().contains("未就绪"));
    }

    @Test
    void getReadyEntityThrowsWhenBelongsToDifferentRepo() {
        CodeRepository repoA = createLocalRepository();
        CodeRepository repoB = createLocalRepository();
        BranchWorkspace workspace = service.prepare(repoA.getId(),
                new CreateBranchWorkspaceRequest("main"));

        assertThrows(ValidationException.class,
                () -> service.getReadyEntity(workspace.getId(), repoB.getId()));
    }

    @Test
    void refreshUpdatesCommitShaWhenChanged() {
        CodeRepository repo = createLocalRepository();
        BranchWorkspace workspace = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));

        // 模拟远端 commit 变化
        Mockito.reset(gitRepositoryService);
        when(gitRepositoryService.commitSha(any(), anyString())).thenReturn("newsha999");
        when(gitRepositoryService.commitMessage(any(), eq("newsha999"))).thenReturn("新提交");

        BranchWorkspace refreshed = service.refresh(workspace.getId());

        assertEquals(BranchWorkspaceStatus.STALE, refreshed.getStatus());
        assertEquals("newsha999", refreshed.getCommitSha());
        assertEquals("新提交", refreshed.getCommitMessage());
    }

    @Test
    void refreshTouchesWhenCommitUnchanged() {
        CodeRepository repo = createLocalRepository();
        BranchWorkspace workspace = service.prepare(repo.getId(),
                new CreateBranchWorkspaceRequest("main"));
        // prepare 后 commitSha = abc123def456（来自 stubGitBasics）

        BranchWorkspace refreshed = service.refresh(workspace.getId());

        assertEquals(BranchWorkspaceStatus.READY, refreshed.getStatus());
        assertEquals("abc123def456", refreshed.getCommitSha());
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建本地仓库（CREATED 状态），localPath 指向已存在目录。
     */
    private CodeRepository createLocalRepository() {
        String name = "it-workspace-" + System.nanoTime();
        CodeRepository repo = new CodeRepository();
        repo.setName(name);
        repo.setLocalPath(name);
        repo.setStatus(RepositoryStatus.CREATED);
        return codeRepositoryRepository.save(repo);
    }
}
