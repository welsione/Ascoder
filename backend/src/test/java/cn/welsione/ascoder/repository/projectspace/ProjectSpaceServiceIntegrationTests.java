package cn.welsione.ascoder.repository.projectspace;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.MockExternalDependencies;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.task.AsyncTask;
import cn.welsione.ascoder.common.task.AsyncTaskJpaRepository;
import cn.welsione.ascoder.common.task.TaskEngine;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskStatus;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.RepositoryBranchSourceKind;
import cn.welsione.ascoder.repository.RepositoryStatus;
import cn.welsione.ascoder.repository.git.GitBranchInfo;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import cn.welsione.ascoder.repository.project.Project;
import cn.welsione.ascoder.repository.project.ProjectJpaRepository;
import cn.welsione.ascoder.repository.project.ProjectRepositoryJpaRepository;
import cn.welsione.ascoder.repository.workspace.BranchWorkspaceJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ProjectSpaceService 集成测试：验证空间创建、prepare/index/reindex/pullRemote 异步任务提交与状态流转。
 *
 * <p>不使用 {@code @Transactional}（异步任务在独立线程池执行，跨线程事务不可见），
 * 改用 {@code @AfterEach} 手动清理测试数据（取消任务 + deleteAll）。</p>
 *
 * <p>通过 {@link MockExternalDependencies} mock GitRepositoryService / CodeGraphClient / GitCredentialStore，
 * 使异步任务不触发真实 git CLI / codegraph CLI。</p>
 */
@Import(MockExternalDependencies.class)
class ProjectSpaceServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private ProjectSpaceService service;

    @Autowired
    private ProjectSpaceJpaRepository projectSpaceRepository;

    @Autowired
    private ProjectSpaceMemberJpaRepository memberRepository;

    @Autowired
    private ProjectJpaRepository projectRepository;

    @Autowired
    private ProjectRepositoryJpaRepository projectMemberRepository;

    @Autowired
    private CodeRepositoryJpaRepository codeRepositoryRepository;

    @Autowired
    private BranchWorkspaceJpaRepository branchWorkspaceRepository;

    @Autowired
    private AsyncTaskJpaRepository taskRepository;

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private IntegrationTestDataFactory factory;

    @Autowired
    private GitRepositoryService gitRepositoryService;

    @Autowired
    private CodeGraphClient codeGraphClient;

    @Value("${ascoder.project-space-root:./data/project-spaces}")
    private String projectSpaceRoot;

    @Value("${ascoder.worktree-root:./data/worktrees}")
    private String worktreeRoot;

    @Value("${ascoder.repo-root:./data/repos}")
    private String repoRoot;

    private final Set<Path> createdDirs = ConcurrentHashMap.newKeySet();
    private final Set<Long> createdSpaceIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> createdRepoIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> createdProjectIds = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() {
        Mockito.reset(gitRepositoryService, codeGraphClient);
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

        // 2. 收集 prepare 任务创建的 BranchWorkspace ID，然后删除成员（解除外键引用）
        Set<Long> workspaceIdsToDelete = ConcurrentHashMap.newKeySet();
        for (Long spaceId : createdSpaceIds) {
            try {
                memberRepository.findByProjectSpace_IdOrderByCreatedAtAsc(spaceId)
                        .forEach(member -> {
                            if (member.getBranchWorkspaceId() != null) {
                                workspaceIdsToDelete.add(member.getBranchWorkspaceId());
                            }
                            memberRepository.delete(member);
                        });
            } catch (Exception ignored) {
            }
        }

        // 3. 清理 prepare 任务创建的 BranchWorkspace
        for (Long workspaceId : workspaceIdsToDelete) {
            try {
                branchWorkspaceRepository.findById(workspaceId)
                        .ifPresent(branchWorkspaceRepository::delete);
            } catch (Exception ignored) {
            }
        }

        // 4. 清理项目空间
        for (Long spaceId : createdSpaceIds) {
            try {
                projectSpaceRepository.findById(spaceId).ifPresent(projectSpaceRepository::delete);
            } catch (Exception ignored) {
            }
        }
        createdSpaceIds.clear();

        // 5. 清理项目仓库成员关联
        for (Long projectId : createdProjectIds) {
            try {
                projectMemberRepository.findByProject_IdOrderBySortOrderAscCreatedAtAsc(projectId)
                        .forEach(projectMemberRepository::delete);
            } catch (Exception ignored) {
            }
            try {
                projectRepository.findById(projectId).ifPresent(projectRepository::delete);
            } catch (Exception ignored) {
            }
        }
        createdProjectIds.clear();

        // 6. 清理仓库
        for (Long repoId : createdRepoIds) {
            try {
                codeRepositoryRepository.findById(repoId).ifPresent(codeRepositoryRepository::delete);
            } catch (Exception ignored) {
            }
        }
        createdRepoIds.clear();

        // 7. 清理异步任务
        taskRepository.deleteAll();

        // 8. 清理测试创建的目录
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
    }

    @Test
    void createProjectSpaceWithMembers() {
        Project project = factory.createProject("it-ps-create-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("space-" + System.nanoTime());
        request.setDefaultBranch("main");

        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        assertNotNull(space.getId());
        assertEquals(ProjectSpaceStatus.CREATED, space.getStatus());
        assertNotNull(space.getRootPath());

        List<ProjectSpaceMember> members = service.members(space.getId());
        assertEquals(1, members.size());
        assertEquals("main", members.get(0).getBranchName());
        assertEquals(repo.getName(), members.get(0).getRepository().getName());
    }

    @Test
    void prepareSubmitsAsyncTaskAndSetsPreparing() throws Exception {
        Project project = factory.createProject("it-ps-prepare-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-prepare-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("prepare-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        // 预创建 worktree 目录（git 被 mock，不会真正创建 worktree）
        createWorktreeDir(repo.getName(), "main");

        stubGitForWorkspace();

        ProjectSpace preparing = service.prepare(space.getId());

        assertEquals(ProjectSpaceStatus.PREPARING, preparing.getStatus());

        // 追踪 prepare 任务创建的项目空间目录（用于后续清理）
        trackProjectSpaceDir(space.getRootPath());

        // 等待异步任务完成
        awaitTerminalByBusinessId(space.getId());

        ProjectSpace after = projectSpaceRepository.findById(space.getId()).orElseThrow();
        assertEquals(ProjectSpaceStatus.READY_TO_INDEX, after.getStatus());
        assertNotNull(after.getLastPreparedAt());
    }

    @Test
    void prepareThrowsWhenAlreadyPreparing() {
        Project project = factory.createProject("it-ps-prep-dup-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-prep-dup-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("prep-dup-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        // 手动设为 PREPARING 状态（不提交异步任务，避免竞态）
        space.preparing();
        projectSpaceRepository.saveAndFlush(space);

        // 已在 PREPARING 状态，再次 prepare 应抛异常
        assertThrows(InvalidStateException.class, () -> service.prepare(space.getId()));
    }

    @Test
    void indexSubmitsCodeGraphIndexTask() throws Exception {
        Project project = factory.createProject("it-ps-index-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-index-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("index-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        // 先 prepare 到 READY_TO_INDEX
        createWorktreeDir(repo.getName(), "main");
        stubGitForWorkspace();
        service.prepare(space.getId());
        trackProjectSpaceDir(space.getRootPath());
        awaitTerminalByBusinessId(space.getId());

        ProjectSpace readyToIndex = projectSpaceRepository.findById(space.getId()).orElseThrow();
        assertEquals(ProjectSpaceStatus.READY_TO_INDEX, readyToIndex.getStatus());

        // stub codegraph index 成功
        when(codeGraphClient.index(any(), any(), any())).thenReturn(
                CodeGraphToolResult.success("索引完成"));

        // index 提交 CODEGRAPH_INDEX 异步任务
        ProjectSpace indexing = service.index(space.getId());
        assertEquals(ProjectSpaceStatus.INDEXING, indexing.getStatus());

        awaitTerminalByBusinessId(space.getId());

        ProjectSpace after = projectSpaceRepository.findById(space.getId()).orElseThrow();
        assertEquals(ProjectSpaceStatus.READY, after.getStatus());
        assertNotNull(after.getLastIndexedAt());
    }

    @Test
    void reindexSubmitsCodeGraphIndexTaskWithIsReindex() throws Exception {
        Project project = factory.createProject("it-ps-reindex-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-reindex-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("reindex-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        // 先 prepare + index 到 READY
        createWorktreeDir(repo.getName(), "main");
        stubGitForWorkspace();
        service.prepare(space.getId());
        trackProjectSpaceDir(space.getRootPath());
        awaitTerminalByBusinessId(space.getId());

        when(codeGraphClient.index(any(), any(), any())).thenReturn(
                CodeGraphToolResult.success("索引完成"));
        service.index(space.getId());
        awaitTerminalByBusinessId(space.getId());

        ProjectSpace ready = projectSpaceRepository.findById(space.getId()).orElseThrow();
        assertEquals(ProjectSpaceStatus.READY, ready.getStatus());

        // 重新索引
        ProjectSpace reindexing = service.reindex(space.getId());
        assertEquals(ProjectSpaceStatus.INDEXING, reindexing.getStatus());

        awaitTerminalByBusinessId(space.getId());

        // 验证提交的是 CODEGRAPH_INDEX 任务（不是 SYNC）
        AsyncTask indexTask = taskRepository.findTopByBusinessIdOrderByQueuedAtDesc(space.getId()).orElseThrow();
        assertEquals(TaskKind.CODEGRAPH_INDEX, indexTask.getKind());

        ProjectSpace after = projectSpaceRepository.findById(space.getId()).orElseThrow();
        assertEquals(ProjectSpaceStatus.READY, after.getStatus());
    }

    @Test
    void reindexRecoveryFromFailedState() throws Exception {
        Project project = factory.createProject("it-ps-recovery-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-recovery-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("recovery-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        // 先 prepare 到 READY_TO_INDEX
        createWorktreeDir(repo.getName(), "main");
        stubGitForWorkspace();
        service.prepare(space.getId());
        trackProjectSpaceDir(space.getRootPath());
        awaitTerminalByBusinessId(space.getId());

        // 第一次 index 失败
        when(codeGraphClient.index(any(), any(), any())).thenReturn(
                CodeGraphToolResult.error("索引失败：解析错误"));
        service.index(space.getId());
        awaitTerminalByBusinessId(space.getId());

        ProjectSpace failed = projectSpaceRepository.findById(space.getId()).orElseThrow();
        assertEquals(ProjectSpaceStatus.FAILED, failed.getStatus());

        // 重新索引作为恢复路径（FAILED 状态可 reindex）
        when(codeGraphClient.index(any(), any(), any())).thenReturn(
                CodeGraphToolResult.success("索引完成"));
        ProjectSpace reindexing = service.reindex(space.getId());
        assertEquals(ProjectSpaceStatus.INDEXING, reindexing.getStatus());

        awaitTerminalByBusinessId(space.getId());

        ProjectSpace after = projectSpaceRepository.findById(space.getId()).orElseThrow();
        assertEquals(ProjectSpaceStatus.READY, after.getStatus());
    }

    @Test
    void indexThrowsWhenNotReadyToIndex() {
        Project project = factory.createProject("it-ps-index-bad-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-index-bad-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("index-bad-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        // CREATED 状态不能直接 index
        assertThrows(InvalidStateException.class, () -> service.index(space.getId()));
    }

    @Test
    void pullRemoteSubmitsGitFetchTaskPerMember() throws Exception {
        Project project = factory.createProject("it-ps-pull-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-pull-repo-" + System.nanoTime(), "main");
        repo.setRemoteUrl("https://github.com/test/repo.git");
        codeRepositoryRepository.save(repo);
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("pull-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        // 创建项目空间目录（refresh 检查目录存在）
        createProjectSpaceDir(space.getRootPath());

        // stub git fetch + 分支查询（GitFetchTaskDefinition 调用 refresh）
        stubGitForFetch();

        service.pullRemote(space.getId());

        // 等待 GIT_FETCH 任务完成
        awaitTerminalByBusinessId(repo.getId());

        // 验证提交了 GIT_FETCH 任务
        AsyncTask fetchTask = taskRepository.findTopByBusinessIdOrderByQueuedAtDesc(repo.getId()).orElseThrow();
        assertEquals(TaskKind.GIT_FETCH, fetchTask.getKind());
    }

    @Test
    void deleteRemovesProjectSpaceAndMembers() {
        Project project = factory.createProject("it-ps-delete-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-delete-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("delete-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        // 创建项目空间目录（delete 会尝试删除）
        createProjectSpaceDir(space.getRootPath());

        Long spaceId = space.getId();
        assertFalse(memberRepository.findByProjectSpace_IdOrderByCreatedAtAsc(spaceId).isEmpty());

        service.delete(spaceId);

        assertTrue(projectSpaceRepository.findById(spaceId).isEmpty());
        assertTrue(memberRepository.findByProjectSpace_IdOrderByCreatedAtAsc(spaceId).isEmpty());
        createdSpaceIds.remove(spaceId);
    }

    @Test
    void deleteThrowsWhenPreparing() {
        Project project = factory.createProject("it-ps-del-prep-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-del-prep-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("del-prep-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        // 手动设为 PREPARING 状态（不提交异步任务，避免竞态）
        space.preparing();
        projectSpaceRepository.saveAndFlush(space);

        assertThrows(InvalidStateException.class, () -> service.delete(space.getId()));
    }

    @Test
    void getThrowsWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(999999L));
    }

    @Test
    void listReturnsAllProjectSpaces() {
        Project project = factory.createProject("it-ps-list-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-list-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("list-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        List<ProjectSpace> all = service.list();
        assertTrue(all.stream().anyMatch(s -> s.getId().equals(space.getId())));
    }

    @Test
    void refreshMarksStaleWhenDirectoryMissing() {
        Project project = factory.createProject("it-ps-refresh-" + System.nanoTime());
        createdProjectIds.add(project.getId());
        CodeRepository repo = createRepository("it-ps-refresh-repo-" + System.nanoTime(), "main");
        factory.createProjectMember(project, repo, repo.getName());

        CreateProjectSpaceRequest request = new CreateProjectSpaceRequest();
        request.setProjectId(project.getId());
        request.setName("refresh-space-" + System.nanoTime());
        request.setDefaultBranch("main");
        ProjectSpace space = service.create(request);
        createdSpaceIds.add(space.getId());

        // 不创建项目空间目录，refresh 应标记 STALE
        ProjectSpace refreshed = service.refresh(space.getId());

        assertEquals(ProjectSpaceStatus.STALE, refreshed.getStatus());
        assertNotNull(refreshed.getLastError());
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建仓库并设置 defaultBranch（CREATED 状态），返回已追踪 id 的实体。
     */
    private CodeRepository createRepository(String name, String defaultBranch) {
        CodeRepository repo = new CodeRepository();
        repo.setName(name);
        repo.setLocalPath(name);
        repo.setDefaultBranch(defaultBranch);
        repo.setStatus(RepositoryStatus.CREATED);
        repo = codeRepositoryRepository.save(repo);
        createdRepoIds.add(repo.getId());
        return repo;
    }

    /**
     * 预创建 worktree 目录，使 prepare 任务中的 createOrReplaceLink 能找到目标目录。
     */
    private void createWorktreeDir(String repoName, String branchName) {
        try {
            String safeRepo = repoName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String safeBranch = branchName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path dir = Path.of(worktreeRoot).toAbsolutePath().normalize().resolve(safeRepo).resolve(safeBranch);
            Files.createDirectories(dir);
            createdDirs.add(dir);
        } catch (IOException e) {
            fail("创建测试 worktree 目录失败: " + e.getMessage());
        }
    }

    /**
     * 预创建项目空间目录。
     */
    private void createProjectSpaceDir(String rootPath) {
        try {
            Path dir = Path.of(projectSpaceRoot).toAbsolutePath().normalize().resolve(rootPath);
            Files.createDirectories(dir);
            createdDirs.add(dir);
        } catch (IOException e) {
            fail("创建测试项目空间目录失败: " + e.getMessage());
        }
    }

    /**
     * 追踪项目空间目录（prepare 任务可能创建），用于后续清理。
     */
    private void trackProjectSpaceDir(String rootPath) {
        Path dir = Path.of(projectSpaceRoot).toAbsolutePath().normalize().resolve(rootPath);
        createdDirs.add(dir);
    }

    /**
     * Stub git 方法使 BranchWorkspaceService.prepare 成功。
     */
    private void stubGitForWorkspace() {
        when(gitRepositoryService.commitSha(any(), anyString())).thenReturn("abc123def456");
        when(gitRepositoryService.commitMessage(any(), anyString())).thenReturn("测试提交");
        Mockito.doNothing().when(gitRepositoryService)
                .createOrUpdateDetachedWorktree(any(), anyString(), anyString(), any());
    }

    /**
     * Stub git 方法使 GitFetchTaskDefinition 成功（fetch + refresh 分支）。
     */
    private void stubGitForFetch() {
        Mockito.doNothing().when(gitRepositoryService).fetch(any());
        when(gitRepositoryService.listBranches(any())).thenReturn(List.of(
                new GitBranchInfo("main", "refs/heads/main", "abc123def456",
                        null, RepositoryBranchSourceKind.LOCAL_HEAD)));
        when(gitRepositoryService.listRemoteHeads(any())).thenReturn(List.of());
        when(gitRepositoryService.commitSha(any(), anyString())).thenReturn("abc123def456");
        when(gitRepositoryService.commitMessage(any(), anyString())).thenReturn("测试提交");
        when(gitRepositoryService.remoteCommitSha(any(), anyString())).thenReturn("abc123def456");
        when(gitRepositoryService.recentCommits(any(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
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
        fail("businessId=" + businessId + " 的异步任务未在 30s 内进入终态，当前状态："
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
