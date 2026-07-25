package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.GitSyncOperation;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import cn.welsione.ascoder.repository.git.GitCredentialStore;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceFetchCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GitFetchTaskDefinition 单元测试，覆盖 fetch/pull 操作、成功/失败状态更新和序列化。
 */
class GitFetchTaskDefinitionTests {

    private static final String REPO_PATH = "/tmp/repos/bar";
    private static final Long REPO_ID = 1L;

    private GitRepositoryService gitRepositoryService;
    private GitCredentialStore gitCredentialStore;
    private RepositoryBranchService repositoryBranchService;
    private CodeRepositoryJpaRepository codeRepositoryJpaRepository;
    private ApplicationEventPublisher eventPublisher;
    private TransactionTemplate transactionTemplate;
    private ObjectMapper objectMapper;
    private GitFetchTaskDefinition definition;
    private TaskProgress progress;

    @BeforeEach
    void setUp() {
        gitRepositoryService = mock(GitRepositoryService.class);
        gitCredentialStore = mock(GitCredentialStore.class);
        repositoryBranchService = mock(RepositoryBranchService.class);
        codeRepositoryJpaRepository = mock(CodeRepositoryJpaRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        transactionTemplate = mock(TransactionTemplate.class);
        objectMapper = new ObjectMapper();
        progress = mock(TaskProgress.class);

        doCallRealMethod().when(progress).checkCancelled();
        when(progress.isCancelled()).thenReturn(false);

        doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> cb = inv.getArgument(0);
            cb.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        definition = new GitFetchTaskDefinition(
                gitRepositoryService, gitCredentialStore, repositoryBranchService,
                codeRepositoryJpaRepository, eventPublisher, transactionTemplate, objectMapper);
    }

    /** 准备一个可保存的 CodeRepository 实体 mock。 */
    private CodeRepository stubEntity() {
        CodeRepository entity = new CodeRepository();
        entity.setId(REPO_ID);
        when(codeRepositoryJpaRepository.findById(REPO_ID)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return entity;
    }

    /** 构建 fetch 上下文，可选字段通过 null 传入。 */
    private GitFetchContext fetchContext(GitSyncOperation operation,
                                         String authUsername, String authPassword,
                                         String remoteUrl, Long projectSpaceId) {
        return new GitFetchContext(REPO_PATH, REPO_ID, operation,
                authUsername, authPassword, remoteUrl, projectSpaceId);
    }

    @Test
    void kindReturnsGitFetch() {
        assertEquals(TaskKind.GIT_FETCH, definition.kind());
    }

    @Test
    void executeFetchOperationCallsFetch() throws Exception {
        CodeRepository entity = stubEntity();

        definition.execute(fetchContext(GitSyncOperation.FETCH, null, null, null, null), progress);

        verify(gitRepositoryService).fetch(eq(Path.of(REPO_PATH)), any());
        verify(gitRepositoryService, never()).pull(any(), any());
        verify(repositoryBranchService).refresh(REPO_ID);
        verify(progress).update(80, "同步完成，正在刷新分支...");
        verify(progress).update(100, "完成");
        assertNotNull(entity.getLastPulledAt());
        assertNull(entity.getLastPullError());
        verify(codeRepositoryJpaRepository).save(entity);
    }

    @Test
    void executePullOperationCallsPull() throws Exception {
        stubEntity();

        definition.execute(fetchContext(GitSyncOperation.PULL, null, null, null, null), progress);

        verify(gitRepositoryService).pull(eq(Path.of(REPO_PATH)), any());
        verify(gitRepositoryService, never()).fetch(any(), any());
        verify(repositoryBranchService).refresh(REPO_ID);
    }

    @Test
    void executeSuccessCallsPulled() throws Exception {
        CodeRepository entity = stubEntity();

        definition.execute(fetchContext(GitSyncOperation.FETCH, null, null, null, null), progress);

        assertNotNull(entity.getLastPulledAt());
        assertNull(entity.getLastPullError());
    }

    @Test
    void executeFailureCallsPullFailed() throws Exception {
        CodeRepository entity = stubEntity();

        doThrow(new RuntimeException("network error"))
                .when(gitRepositoryService).fetch(any(), any());

        definition.execute(fetchContext(GitSyncOperation.FETCH, null, null, null, null), progress);

        // fetch 失败不向上抛异常，而是记录错误信息到实体
        assertEquals("network error", entity.getLastPullError());
        assertNull(entity.getLastPulledAt());
        verify(codeRepositoryJpaRepository).save(entity);
        verify(repositoryBranchService).refresh(REPO_ID);
        verify(progress).update(100, "完成");
    }

    @Test
    void executeWithCredentialsCallsUpsert() throws Exception {
        stubEntity();

        definition.execute(fetchContext(GitSyncOperation.FETCH,
                "user", "pass", "https://github.com/foo/bar.git", null), progress);

        verify(gitCredentialStore).upsert("https://github.com/foo/bar.git", "user", "pass");
    }

    @Test
    void executeWithoutRemoteUrlDoesNotCallUpsert() throws Exception {
        stubEntity();

        definition.execute(fetchContext(GitSyncOperation.FETCH, "user", "pass", null, null), progress);

        verify(gitCredentialStore, never()).upsert(anyString(), anyString(), anyString());
    }

    @Test
    void serializeAndDeserializeContextRoundTrip() {
        GitFetchContext context = fetchContext(GitSyncOperation.FETCH,
                "user", "pass", "https://github.com/foo/bar.git", null);

        String json = definition.serializeContext(context);
        GitFetchContext deserialized = definition.deserializeContext(json);

        assertEquals(context, deserialized);
    }

    @Test
    void executeWithProjectSpaceIdPublishesEvent() throws Exception {
        stubEntity();

        definition.execute(fetchContext(GitSyncOperation.FETCH, null, null, null, 42L), progress);

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProjectSpaceFetchCompletedEvent event = captor.getAllValues().stream()
                .filter(e -> e instanceof ProjectSpaceFetchCompletedEvent)
                .map(e -> (ProjectSpaceFetchCompletedEvent) e)
                .findFirst()
                .orElse(null);
        assertNotNull(event);
        assertEquals(42L, event.getProjectSpaceId());
    }

    @Test
    void executeWithoutProjectSpaceIdDoesNotPublishEvent() throws Exception {
        stubEntity();

        definition.execute(fetchContext(GitSyncOperation.FETCH, null, null, null, null), progress);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deserializeOldContextWithoutOperationDefaultsToFetch() throws Exception {
        // 向后兼容：旧任务上下文 JSON 不含 operation 字段，反序列化后应为 null，execute 默认 FETCH
        stubEntity();

        String oldJson = "{\"repositoryPath\":\"" + REPO_PATH + "\",\"repositoryId\":" + REPO_ID + "}";
        GitFetchContext context = definition.deserializeContext(oldJson);

        assertNull(context.getOperation());
        definition.execute(context, progress);

        verify(gitRepositoryService).fetch(eq(Path.of(REPO_PATH)), any());
        verify(gitRepositoryService, never()).pull(any(), any());
    }
}
