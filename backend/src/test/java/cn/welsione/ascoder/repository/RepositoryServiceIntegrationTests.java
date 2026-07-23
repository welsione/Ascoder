package cn.welsione.ascoder.repository;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.MockExternalDependencies;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.task.AsyncTask;
import cn.welsione.ascoder.common.task.AsyncTaskJpaRepository;
import cn.welsione.ascoder.common.task.TaskEngine;
import cn.welsione.ascoder.common.task.TaskStatus;
import cn.welsione.ascoder.repository.git.GitBranchInfo;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RepositoryService 集成测试：验证仓库创建（本地/远程）、fetch/pull/index 异步任务提交与状态流转。
 *
 * <p>不使用 {@code @Transactional}（异步任务在独立线程池执行，跨线程事务不可见），
 * 改用 {@code @AfterEach} 手动清理测试数据。</p>
 *
 * <p>通过 {@link MockExternalDependencies} mock GitRepositoryService / CodeGraphClient / GitCredentialStore，
 * 使异步任务不触发真实 git CLI / codegraph CLI。</p>
 */
@Import(MockExternalDependencies.class)
class RepositoryServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private RepositoryService service;

    @Autowired
    private CodeRepositoryJpaRepository repository;

    @Autowired
    private AsyncTaskJpaRepository taskRepository;

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private GitRepositoryService gitRepositoryService;

    @Autowired
    private CodeGraphClient codeGraphClient;

    @Autowired
    private RepositoryBranchJpaRepository branchRepository;

    private Path repoRoot;
    private final Set<Long> createdRepoIds = ConcurrentHashMap.newKeySet();
    private final Set<Path> createdDirs = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() throws IOException {
        Mockito.reset(gitRepositoryService, codeGraphClient);
        repoRoot = Path.of("./data/repos").toAbsolutePath().normalize();
        Files.createDirectories(repoRoot);
    }

    @AfterEach
    void cleanup() throws InterruptedException {
        // 1. 取消并等待所有未完成任务终态
        List<AsyncTask> running = taskRepository.findByStatusIn(
                List.of(TaskStatus.QUEUED, TaskStatus.RUNNING));
        for (AsyncTask t : running) {
            try {
                taskEngine.cancel(t.getId());
            } catch (Exception ignored) {
            }
        }
        for (AsyncTask t : running) {
            try {
                awaitTerminal(t.getId());
            } catch (Exception ignored) {
            }
        }

        // 2. 清理测试创建的仓库及其分支
        for (Long repoId : createdRepoIds) {
            try {
                branchRepository.findByRepository_IdOrderByNameAscSourceKindAsc(repoId)
                        .forEach(branchRepository::delete);
            } catch (Exception ignored) {
            }
            try {
                repository.findById(repoId).ifPresent(repository::delete);
            } catch (Exception ignored) {
            }
        }
        createdRepoIds.clear();

        // 3. 清理测试创建的目录
        for (Path dir : createdDirs) {
            try {
                if (Files.exists(dir)) {
                    Files.walk(dir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
            } catch (IOException ignored) {
            }
        }
        createdDirs.clear();

        // 4. 清理异步任务
        taskRepository.deleteAll();
    }

    @Test
    void createLocalRepositoryReturnsCreatedStatus() {
        String name = "it-local-" + System.nanoTime();
        createLocalDir(name);

        CodeRepository created = service.create(new CreateRepositoryRequest(
                name, name, null, null, null, null));
        createdRepoIds.add(created.getId());

        assertNotNull(created.getId());
        assertEquals(name, created.getName());
        assertEquals(RepositoryStatus.CREATED, created.getStatus());
        assertNull(created.getRemoteUrl());
    }

    @Test
    void createRemoteRepositorySubmitsCloneTask() throws Exception {
        String name = "it-remote-" + System.nanoTime();

        // stub git 操作，使异步 clone 任务成功完成
        stubGitBranchesForRefresh();

        CodeRepository created = service.create(new CreateRepositoryRequest(
                name, name, "https://github.com/test/repo.git", "main", null, null));
        createdRepoIds.add(created.getId());

        // 提交后立即处于 CLONING 状态
        assertEquals(RepositoryStatus.CLONING, created.getStatus());
        assertNotNull(created.getRemoteUrl());

        // 等待异步任务完成，clone 完成后状态回到 CREATED
        awaitTerminalByBusinessId(created.getId());
        CodeRepository afterClone = repository.findById(created.getId()).orElseThrow();
        assertEquals(RepositoryStatus.CREATED, afterClone.getStatus());
        assertNotNull(afterClone.getLastPulledAt());
    }

    @Test
    void fetchSubmitsGitFetchTask() throws Exception {
        String name = "it-fetch-" + System.nanoTime();
        createLocalDir(name);
        CodeRepository repo = service.create(new CreateRepositoryRequest(
                name, name, null, null, null, null));
        createdRepoIds.add(repo.getId());

        stubGitBranchesForRefresh();

        CodeRepository fetched = service.fetch(repo.getId());
        assertEquals(RepositoryStatus.SYNCING, fetched.getStatus());

        awaitTerminalByBusinessId(repo.getId());

        // fetch 完成后 lastPulledAt 被设置
        CodeRepository afterFetch = repository.findById(repo.getId()).orElseThrow();
        assertNotNull(afterFetch.getLastPulledAt());
    }

    @Test
    void pullSubmitsGitFetchTask() throws Exception {
        String name = "it-pull-" + System.nanoTime();
        createLocalDir(name);
        CodeRepository repo = service.create(new CreateRepositoryRequest(
                name, name, null, null, null, null));
        createdRepoIds.add(repo.getId());

        stubGitBranchesForRefresh();

        CodeRepository pulled = service.pull(repo.getId());
        assertEquals(RepositoryStatus.SYNCING, pulled.getStatus());

        awaitTerminalByBusinessId(repo.getId());

        CodeRepository afterPull = repository.findById(repo.getId()).orElseThrow();
        assertNotNull(afterPull.getLastPulledAt());
    }

    @Test
    void indexSubmitsCodeGraphIndexTask() throws Exception {
        String name = "it-index-" + System.nanoTime();
        createLocalDir(name);
        CodeRepository repo = service.create(new CreateRepositoryRequest(
                name, name, null, null, null, null));
        createdRepoIds.add(repo.getId());

        when(codeGraphClient.index(any(Path.class))).thenReturn(
                CodeGraphToolResult.success("索引完成"));

        CodeRepository indexed = service.index(repo.getId());
        assertEquals(RepositoryStatus.INDEXING, indexed.getStatus());

        awaitTerminalByBusinessId(repo.getId());

        // 索引完成后状态变为 READY
        CodeRepository afterIndex = repository.findById(repo.getId()).orElseThrow();
        assertEquals(RepositoryStatus.READY, afterIndex.getStatus());
        assertNotNull(afterIndex.getLastIndexedAt());
    }

    @Test
    void indexThrowsWhenAlreadyIndexing() {
        String name = "it-index-dup-" + System.nanoTime();
        createLocalDir(name);
        CodeRepository repo = service.create(new CreateRepositoryRequest(
                name, name, null, null, null, null));
        createdRepoIds.add(repo.getId());

        when(codeGraphClient.index(any(Path.class))).thenReturn(
                CodeGraphToolResult.success("索引完成"));
        service.index(repo.getId()); // 第一次索引

        // 已经在 INDEXING 状态，再次索引应抛异常
        assertThrows(cn.welsione.ascoder.common.exception.InvalidStateException.class,
                () -> service.index(repo.getId()));
    }

    @Test
    void createDuplicateNameThrowsDuplicateException() {
        String name = "it-dup-" + System.nanoTime();
        createLocalDir(name);
        CodeRepository repo = service.create(new CreateRepositoryRequest(
                name, name, null, null, null, null));
        createdRepoIds.add(repo.getId());

        assertThrows(DuplicateException.class, () ->
                service.create(new CreateRepositoryRequest(
                        name, name + "-2", null, null, null, null)));
    }

    @Test
    void getThrowsWhenNotFound() {
        assertThrows(cn.welsione.ascoder.common.exception.ResourceNotFoundException.class,
                () -> service.get(999999L));
    }

    @Test
    void listReturnsAllRepositories() {
        String name = "it-list-" + System.nanoTime();
        createLocalDir(name);
        CodeRepository repo = service.create(new CreateRepositoryRequest(
                name, name, null, null, null, null));
        createdRepoIds.add(repo.getId());

        List<CodeRepository> all = service.list();
        assertTrue(all.stream().anyMatch(r -> r.getId().equals(repo.getId())));
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建本地仓库目录（validateUnderRoot 要求路径存在）。
     */
    private void createLocalDir(String name) {
        try {
            Path dir = repoRoot.resolve(name);
            Files.createDirectories(dir);
            createdDirs.add(dir);
        } catch (IOException e) {
            fail("创建测试目录失败: " + e.getMessage());
        }
    }

    /**
     * Stub GitRepositoryService 的分支查询方法，使 RepositoryBranchService.refresh 成功。
     */
    private void stubGitBranchesForRefresh() {
        when(gitRepositoryService.listBranches(any())).thenReturn(List.of(
                new GitBranchInfo("main", "refs/heads/main", "abc123def456",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD)));
        when(gitRepositoryService.listRemoteHeads(any())).thenReturn(List.of());
        when(gitRepositoryService.currentBranch(any())).thenReturn("main");
    }

    /**
     * 等待指定 businessId 的异步任务进入终态。
     */
    private void awaitTerminalByBusinessId(Long businessId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            AsyncTask task = taskRepository.findTopByBusinessIdOrderByQueuedAtDesc(businessId)
                    .orElse(null);
            if (task != null && task.isTerminal()) {
                return;
            }
            Thread.sleep(200);
        }
        AsyncTask task = taskRepository.findTopByBusinessIdOrderByQueuedAtDesc(businessId)
                .orElse(null);
        fail("仓库 " + businessId + " 的异步任务未在 30s 内进入终态，当前状态："
                + (task != null ? task.getStatus() : "null"));
    }

    /**
     * 等待指定 taskId 的异步任务进入终态。
     */
    private void awaitTerminal(Long taskId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            AsyncTask task = taskRepository.findById(taskId).orElse(null);
            if (task != null && task.isTerminal()) {
                return;
            }
            Thread.sleep(100);
        }
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        fail("任务 " + taskId + " 未在 10s 内进入终态，当前状态："
                + (task != null ? task.getStatus() : "null"));
    }
}
