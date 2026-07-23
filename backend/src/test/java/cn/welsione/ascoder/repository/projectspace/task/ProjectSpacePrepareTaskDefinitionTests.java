package cn.welsione.ascoder.repository.projectspace.task;

import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMember;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMemberJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceStatus;
import cn.welsione.ascoder.repository.workspace.BranchWorkspace;
import cn.welsione.ascoder.repository.workspace.BranchWorkspaceService;
import cn.welsione.ascoder.repository.workspace.CreateBranchWorkspaceRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProjectSpacePrepareTaskDefinition 单元测试，覆盖成员准备成功/失败路径及序列化。
 */
class ProjectSpacePrepareTaskDefinitionTests {

    @TempDir
    Path tempDir;

    private ProjectSpaceJpaRepository projectSpaceJpaRepository;
    private ProjectSpaceMemberJpaRepository memberJpaRepository;
    private BranchWorkspaceService branchWorkspaceService;
    private TransactionTemplate transactionTemplate;
    private ObjectMapper objectMapper;
    private ProjectSpacePrepareTaskDefinition definition;
    private TaskProgress progress;

    private Path projectSpaceRoot;
    private Path worktreeRoot;

    @BeforeEach
    void setUp() throws Exception {
        projectSpaceJpaRepository = mock(ProjectSpaceJpaRepository.class);
        memberJpaRepository = mock(ProjectSpaceMemberJpaRepository.class);
        branchWorkspaceService = mock(BranchWorkspaceService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        objectMapper = new ObjectMapper();
        progress = mock(TaskProgress.class);

        doCallRealMethod().when(progress).checkCancelled();
        when(progress.isCancelled()).thenReturn(false);

        // TransactionTemplate.executeWithoutResult：直接执行 lambda 体
        doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> cb = inv.getArgument(0);
            cb.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // TransactionTemplate.execute（返回值）：直接执行 callback 并返回结果
        doAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        }).when(transactionTemplate).execute(any());

        projectSpaceRoot = tempDir.resolve("project-spaces");
        worktreeRoot = tempDir.resolve("worktrees");

        definition = new ProjectSpacePrepareTaskDefinition(
                projectSpaceJpaRepository, memberJpaRepository,
                branchWorkspaceService, transactionTemplate, objectMapper);

        // 注入 @Value 字段
        ReflectionTestUtils.setField(definition, "projectSpaceRoot", projectSpaceRoot.toString());
        ReflectionTestUtils.setField(definition, "worktreeRoot", worktreeRoot.toString());
    }

    @Test
    void kindReturnsProjectSpacePrepare() {
        assertEquals(TaskKind.PROJECT_SPACE_PREPARE, definition.kind());
    }

    @Test
    void executeSuccess() throws Exception {
        // 准备 ProjectSpace 实体（rootPath 为相对路径，运行时拼接 projectSpaceRoot）
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        space.setName("test-space");
        space.setRootPath("test-space");
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 准备成员实体
        CodeRepository repo = new CodeRepository();
        repo.setId(10L);
        ProjectSpaceMember member = new ProjectSpaceMember();
        member.setId(10L);
        member.setRepository(repo);
        member.setBranchName("main");
        member.setAlias("repo-alias");
        member.setCommitSha("abc123");
        space.setStatus(ProjectSpaceStatus.PREPARING);

        when(memberJpaRepository.findByProjectSpace_IdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(member));
        when(memberJpaRepository.findById(10L)).thenReturn(Optional.of(member));
        when(memberJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 创建 worktree 实际目录，使 createOrReplaceLink 中 Files.exists(targetPath) 通过
        Path actualWorktreeDir = worktreeRoot.resolve("repo-name").resolve("main");
        Files.createDirectories(actualWorktreeDir);

        BranchWorkspace workspace = new BranchWorkspace();
        workspace.setId(100L);
        workspace.setBranchName("main");
        workspace.setCommitSha("abc123");
        workspace.setCommitMessage("initial commit");
        workspace.setWorktreePath("repo-name/main");
        workspace.setCodegraphIndexPath("repo-name/main/.codegraph");

        when(branchWorkspaceService.prepare(eq(10L), any(CreateBranchWorkspaceRequest.class), eq("abc123")))
                .thenReturn(workspace);

        Map<String, String> context = Map.of("projectSpaceId", "1");

        definition.execute(context, progress);

        // 验证 branchWorkspaceService.prepare 被调用
        verify(branchWorkspaceService).prepare(eq(10L),
                argThat(req -> "main".equals(req.getBranchName())), eq("abc123"));

        // 验证实体状态更新为 READY_TO_INDEX
        verify(projectSpaceJpaRepository, atLeastOnce()).save(space);
        assertEquals(ProjectSpaceStatus.READY_TO_INDEX, space.getStatus());
        assertNotNull(space.getLastPreparedAt());

        // 验证成员更新
        verify(memberJpaRepository, atLeastOnce()).save(member);
        assertEquals(workspace, member.getBranchWorkspace());
        assertEquals("abc123", member.getCommitSha());
        assertNotNull(member.getLinkPath());

        // 验证进度更新
        verify(progress).update(100, "准备完成");

        // 验证符号链接已创建
        Path linkPath = projectSpaceRoot.resolve("test-space").resolve("repo-alias");
        assertTrue(Files.isSymbolicLink(linkPath), "应创建符号链接");
    }

    @Test
    void executeNoMembersSuccess() throws Exception {
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        space.setName("empty-space");
        space.setRootPath("empty-space");
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(memberJpaRepository.findByProjectSpace_IdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());

        Map<String, String> context = Map.of("projectSpaceId", "1");

        definition.execute(context, progress);

        // 无成员时直接更新状态为 READY_TO_INDEX
        assertEquals(ProjectSpaceStatus.READY_TO_INDEX, space.getStatus());
        assertNotNull(space.getLastPreparedAt());
        verify(branchWorkspaceService, never()).prepare(anyLong(), any(), any());
        verify(progress).update(100, "准备完成");
    }

    @Test
    void executeMemberPrepareThrowsFailure() throws Exception {
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        space.setName("fail-space");
        space.setRootPath("fail-space");
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CodeRepository repo = new CodeRepository();
        repo.setId(10L);
        ProjectSpaceMember member = new ProjectSpaceMember();
        member.setId(10L);
        member.setRepository(repo);
        member.setBranchName("main");
        member.setAlias("bad-alias");
        member.setCommitSha("abc123");

        when(memberJpaRepository.findByProjectSpace_IdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(member));

        when(branchWorkspaceService.prepare(eq(10L), any(CreateBranchWorkspaceRequest.class), eq("abc123")))
                .thenThrow(new RuntimeException("git fetch 失败"));

        Map<String, String> context = Map.of("projectSpaceId", "1");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> definition.execute(context, progress));
        assertTrue(ex.getMessage().contains("准备成员 bad-alias 失败"));
        assertTrue(ex.getMessage().contains("git fetch 失败"));

        // 验证实体状态更新为 FAILED
        verify(projectSpaceJpaRepository, atLeastOnce()).save(space);
        assertEquals(ProjectSpaceStatus.FAILED, space.getStatus());
        assertNotNull(space.getLastError());
        assertTrue(space.getLastError().contains("git fetch 失败"));

        // 验证未到达最终进度更新
        verify(progress, never()).update(100, "准备完成");
    }

    @Test
    void serializeAndDeserializeContextRoundTrip() {
        Map<String, String> context = Map.of("projectSpaceId", "1");

        String json = definition.serializeContext(context);
        Map<String, String> deserialized = definition.deserializeContext(json);

        assertEquals(context, deserialized);
    }
}
