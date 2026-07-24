package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.GitSyncOperation;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import cn.welsione.ascoder.repository.git.GitCredentialStore;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GitFetchTaskDefinition 单元测试，覆盖 fetch/pull 操作、成功/失败状态更新和序列化。
 */
class GitFetchTaskDefinitionTests {

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

    @Test
    void kindReturnsGitFetch() {
        assertEquals(TaskKind.GIT_FETCH, definition.kind());
    }

    @Test
    void executeFetchOperationCallsFetch() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "repositoryId", "1",
                "operation", GitSyncOperation.FETCH.code()
        );

        definition.execute(context, progress);

        verify(gitRepositoryService).fetch(eq(Path.of("/tmp/repos/bar")), any());
        verify(gitRepositoryService, never()).pull(any(), any());
        verify(repositoryBranchService).refresh(1L);
        verify(progress).update(80, "同步完成，正在刷新分支...");
        verify(progress).update(100, "完成");
        assertNotNull(entity.getLastPulledAt());
        assertNull(entity.getLastPullError());
        verify(codeRepositoryJpaRepository).save(entity);
    }

    @Test
    void executePullOperationCallsPull() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "repositoryId", "1",
                "operation", GitSyncOperation.PULL.code()
        );

        definition.execute(context, progress);

        verify(gitRepositoryService).pull(eq(Path.of("/tmp/repos/bar")), any());
        verify(gitRepositoryService, never()).fetch(any(), any());
        verify(repositoryBranchService).refresh(1L);
    }

    @Test
    void executeSuccessCallsPulled() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "repositoryId", "1",
                "operation", GitSyncOperation.FETCH.code()
        );

        definition.execute(context, progress);

        assertNotNull(entity.getLastPulledAt());
        assertNull(entity.getLastPullError());
    }

    @Test
    void executeFailureCallsPullFailed() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        doThrow(new RuntimeException("network error"))
                .when(gitRepositoryService).fetch(any(), any());

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "repositoryId", "1",
                "operation", GitSyncOperation.FETCH.code()
        );

        definition.execute(context, progress);

        // fetch 失败不向上抛异常，而是记录错误信息到实体
        assertEquals("network error", entity.getLastPullError());
        assertNull(entity.getLastPulledAt());
        verify(codeRepositoryJpaRepository).save(entity);
        verify(repositoryBranchService).refresh(1L);
        verify(progress).update(100, "完成");
    }

    @Test
    void executeWithCredentialsCallsUpsert() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = new java.util.HashMap<>();
        context.put("repositoryPath", "/tmp/repos/bar");
        context.put("repositoryId", "1");
        context.put("operation", GitSyncOperation.FETCH.code());
        context.put("authUsername", "user");
        context.put("authPassword", "pass");
        context.put("remoteUrl", "https://github.com/foo/bar.git");

        definition.execute(context, progress);

        verify(gitCredentialStore).upsert("https://github.com/foo/bar.git", "user", "pass");
    }

    @Test
    void executeWithoutRemoteUrlDoesNotCallUpsert() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = new java.util.HashMap<>();
        context.put("repositoryPath", "/tmp/repos/bar");
        context.put("repositoryId", "1");
        context.put("operation", GitSyncOperation.FETCH.code());
        context.put("authUsername", "user");
        context.put("authPassword", "pass");

        definition.execute(context, progress);

        verify(gitCredentialStore, never()).upsert(anyString(), anyString(), anyString());
    }

    @Test
    void serializeAndDeserializeContextRoundTrip() {
        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "repositoryId", "1",
                "operation", GitSyncOperation.FETCH.code(),
                "authUsername", "user",
                "authPassword", "pass",
                "remoteUrl", "https://github.com/foo/bar.git"
        );

        String json = definition.serializeContext(context);
        Map<String, String> deserialized = definition.deserializeContext(json);

        assertEquals(context, deserialized);
    }

    @Test
    void executeWithProjectSpaceIdPublishesEvent() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = new java.util.HashMap<>();
        context.put("repositoryPath", "/tmp/repos/bar");
        context.put("repositoryId", "1");
        context.put("operation", GitSyncOperation.FETCH.code());
        context.put("projectSpaceId", "42");

        definition.execute(context, progress);

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getAllValues().stream()
                .filter(e -> e instanceof cn.welsione.ascoder.repository.projectspace.ProjectSpaceFetchCompletedEvent)
                .map(e -> (cn.welsione.ascoder.repository.projectspace.ProjectSpaceFetchCompletedEvent) e)
                .findFirst()
                .orElse(null);
        assertNotNull(event);
        assertEquals(42L, event.getProjectSpaceId());
    }

    @Test
    void executeWithoutProjectSpaceIdDoesNotPublishEvent() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "repositoryId", "1",
                "operation", GitSyncOperation.FETCH.code()
        );

        definition.execute(context, progress);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executeWithoutOperationDefaultsToFetch() throws Exception {
        // 向后兼容：旧任务上下文可能不含 operation 字段，应默认为 fetch
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = new java.util.HashMap<>();
        context.put("repositoryPath", "/tmp/repos/bar");
        context.put("repositoryId", "1");

        definition.execute(context, progress);

        verify(gitRepositoryService).fetch(eq(Path.of("/tmp/repos/bar")), any());
        verify(gitRepositoryService, never()).pull(any(), any());
    }
}
