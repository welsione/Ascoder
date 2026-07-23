package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskCancelledException;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import cn.welsione.ascoder.repository.RepositoryStatus;
import cn.welsione.ascoder.repository.git.GitCredentialStore;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GitCloneTaskDefinition 单元测试，覆盖克隆流程、凭据写入、分支推断、取消传播和序列化。
 */
class GitCloneTaskDefinitionTests {

    private GitRepositoryService gitRepositoryService;
    private GitCredentialStore gitCredentialStore;
    private RepositoryBranchService repositoryBranchService;
    private CodeRepositoryJpaRepository codeRepositoryJpaRepository;
    private TransactionTemplate transactionTemplate;
    private ObjectMapper objectMapper;
    private GitCloneTaskDefinition definition;
    private TaskProgress progress;

    @BeforeEach
    void setUp() {
        gitRepositoryService = mock(GitRepositoryService.class);
        gitCredentialStore = mock(GitCredentialStore.class);
        repositoryBranchService = mock(RepositoryBranchService.class);
        codeRepositoryJpaRepository = mock(CodeRepositoryJpaRepository.class);
        transactionTemplate = mock(TransactionTemplate.class);
        objectMapper = new ObjectMapper();
        progress = mock(TaskProgress.class);

        // progress.checkCancelled() 走 default 实现，需调用真实方法使其依赖 isCancelled()
        doCallRealMethod().when(progress).checkCancelled();
        when(progress.isCancelled()).thenReturn(false);

        // TransactionTemplate.executeWithoutResult：直接执行 lambda 体
        doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> cb = inv.getArgument(0);
            cb.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        definition = new GitCloneTaskDefinition(
                gitRepositoryService, gitCredentialStore, repositoryBranchService,
                codeRepositoryJpaRepository, transactionTemplate, objectMapper);
    }

    @Test
    void kindReturnsGitClone() {
        assertEquals(TaskKind.GIT_CLONE, definition.kind());
    }

    @Test
    void executeSuccessClonesAndRefreshesBranches() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = Map.of(
                "remoteUrl", "https://github.com/foo/bar.git",
                "targetPath", "/tmp/repos/bar",
                "branchName", "main",
                "repositoryId", "1"
        );

        definition.execute(context, progress);

        verify(gitRepositoryService).cloneRepository(eq("https://github.com/foo/bar.git"),
                eq(Path.of("/tmp/repos/bar")), eq("main"));
        verify(repositoryBranchService).refresh(1L);
        verify(progress).update(50, "克隆完成，正在刷新分支...");
        verify(progress).update(100, "完成");
        verify(codeRepositoryJpaRepository).save(entity);
        assertEquals(RepositoryStatus.CREATED, entity.getStatus());
        assertNotNull(entity.getLastPulledAt());
        assertEquals("main", entity.getDefaultBranch());
    }

    @Test
    void executeWithCredentialsCallsUpsert() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = new java.util.HashMap<>();
        context.put("remoteUrl", "https://github.com/foo/bar.git");
        context.put("targetPath", "/tmp/repos/bar");
        context.put("branchName", "main");
        context.put("repositoryId", "1");
        context.put("authUsername", "user");
        context.put("authPassword", "pass");

        definition.execute(context, progress);

        verify(gitCredentialStore).upsert("https://github.com/foo/bar.git", "user", "pass");
    }

    @Test
    void executeWithoutCredentialsDoesNotCallUpsert() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> context = Map.of(
                "remoteUrl", "https://github.com/foo/bar.git",
                "targetPath", "/tmp/repos/bar",
                "branchName", "main",
                "repositoryId", "1"
        );

        definition.execute(context, progress);

        verify(gitCredentialStore, never()).upsert(anyString(), anyString(), anyString());
    }

    @Test
    void executeCloneThrowsExceptionPropagates() {
        doThrow(new IllegalStateException("clone failed"))
                .when(gitRepositoryService).cloneRepository(anyString(), any(), any());

        Map<String, String> context = Map.of(
                "remoteUrl", "https://github.com/foo/bar.git",
                "targetPath", "/tmp/repos/bar",
                "branchName", "main",
                "repositoryId", "1"
        );

        assertThrows(IllegalStateException.class, () -> definition.execute(context, progress));
        verify(repositoryBranchService, never()).refresh(anyLong());
    }

    @Test
    void executeBlankBranchNameInfersFromCurrentBranch() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(1L);
        when(codeRepositoryJpaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gitRepositoryService.currentBranch(Path.of("/tmp/repos/bar"))).thenReturn("develop");

        Map<String, String> context = Map.of(
                "remoteUrl", "https://github.com/foo/bar.git",
                "targetPath", "/tmp/repos/bar",
                "branchName", "",
                "repositoryId", "1"
        );

        definition.execute(context, progress);

        verify(gitRepositoryService).currentBranch(Path.of("/tmp/repos/bar"));
        assertEquals("develop", entity.getDefaultBranch());
    }

    @Test
    void executeProgressCancelledPropagates() {
        // checkCancelled() 在 clone 完成后调用，直接令 isCancelled 返回 true
        when(progress.isCancelled()).thenReturn(true);

        Map<String, String> context = Map.of(
                "remoteUrl", "https://github.com/foo/bar.git",
                "targetPath", "/tmp/repos/bar",
                "branchName", "main",
                "repositoryId", "1"
        );

        assertThrows(TaskCancelledException.class, () -> definition.execute(context, progress));
    }

    @Test
    void serializeAndDeserializeContextRoundTrip() {
        Map<String, String> context = Map.of(
                "remoteUrl", "https://github.com/foo/bar.git",
                "targetPath", "/tmp/repos/bar",
                "branchName", "main",
                "repositoryId", "1",
                "authUsername", "user",
                "authPassword", "pass"
        );

        String json = definition.serializeContext(context);
        Map<String, String> deserialized = definition.deserializeContext(json);

        assertEquals(context, deserialized);
    }
}
