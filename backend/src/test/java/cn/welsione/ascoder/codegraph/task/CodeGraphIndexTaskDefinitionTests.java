package cn.welsione.ascoder.codegraph.task;

import cn.welsione.ascoder.codegraph.infrastructure.cli.IndexProgressTracker;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.RepositoryStatus;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceStatus;
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
 * CodeGraphIndexTaskDefinition 单元测试，覆盖项目空间级索引、仓库级索引的成功/失败路径及序列化。
 */
class CodeGraphIndexTaskDefinitionTests {

    private CodeGraphClient codeGraphClient;
    private IndexProgressTracker indexProgressTracker;
    private ProjectSpaceJpaRepository projectSpaceJpaRepository;
    private CodeRepositoryJpaRepository codeRepositoryJpaRepository;
    private TransactionTemplate transactionTemplate;
    private ObjectMapper objectMapper;
    private CodeGraphIndexTaskDefinition definition;
    private TaskProgress progress;

    @BeforeEach
    void setUp() {
        codeGraphClient = mock(CodeGraphClient.class);
        indexProgressTracker = mock(IndexProgressTracker.class);
        projectSpaceJpaRepository = mock(ProjectSpaceJpaRepository.class);
        codeRepositoryJpaRepository = mock(CodeRepositoryJpaRepository.class);
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

        // IndexProgressTracker.get 返回非 null 进度，避免进度同步线程 NPE
        when(indexProgressTracker.get(anyLong()))
                .thenReturn(new IndexProgressTracker.IndexProgress(0, "进行中", false));

        definition = new CodeGraphIndexTaskDefinition(
                codeGraphClient, indexProgressTracker, projectSpaceJpaRepository,
                codeRepositoryJpaRepository, transactionTemplate, objectMapper);
    }

    // ---- kind() ----

    @Test
    void kindReturnsCodegraphIndex() {
        assertEquals(TaskKind.CODEGRAPH_INDEX, definition.kind());
    }

    // ---- 项目空间级索引 ----

    @Test
    void executeProjectSpaceIndexSuccess() throws Exception {
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.index(any(Path.class), anyLong()))
                .thenReturn(CodeGraphToolResult.success("索引完成"));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "projectSpaceId", "1"
        );

        definition.execute(context, progress);

        verify(codeGraphClient).index(Path.of("/tmp/repos/bar"), 1L);
        verify(indexProgressTracker).complete(1L);
        verify(progress).update(100, "索引完成");
        verify(projectSpaceJpaRepository).save(space);
        assertEquals(ProjectSpaceStatus.READY, space.getStatus());
        assertNotNull(space.getLastIndexedAt());
    }

    @Test
    void executeProjectSpaceIndexWithCodegraphPath() throws Exception {
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.index(any(Path.class), any(Path.class), anyLong()))
                .thenReturn(CodeGraphToolResult.success("索引完成"));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "codegraphIndexPath", "/tmp/repos/bar/.codegraph",
                "projectSpaceId", "1"
        );

        definition.execute(context, progress);

        verify(codeGraphClient).index(
                Path.of("/tmp/repos/bar"),
                Path.of("/tmp/repos/bar/.codegraph"),
                1L);
        verify(indexProgressTracker).complete(1L);
        assertEquals(ProjectSpaceStatus.READY, space.getStatus());
    }

    @Test
    void executeProjectSpaceIndexFailureResult() {
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.index(any(Path.class), anyLong()))
                .thenReturn(CodeGraphToolResult.error("索引引擎错误"));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "projectSpaceId", "1"
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> definition.execute(context, progress));
        assertTrue(ex.getMessage().startsWith("CodeGraph 索引失败"));

        verify(indexProgressTracker).fail(1L, "索引引擎错误");
        verify(progress).update(0, "索引失败");
        verify(projectSpaceJpaRepository).save(space);
        assertEquals(ProjectSpaceStatus.FAILED, space.getStatus());
        assertEquals("索引引擎错误", space.getLastError());
    }

    @Test
    void executeProjectSpaceIndexClientThrowsException() {
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.index(any(Path.class), anyLong()))
                .thenThrow(new RuntimeException("CLI 连接超时"));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "projectSpaceId", "1"
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> definition.execute(context, progress));
        assertEquals("CLI 连接超时", ex.getMessage());

        verify(indexProgressTracker).fail(1L, "CLI 连接超时");
        verify(progress).update(0, "索引失败");
        assertEquals(ProjectSpaceStatus.FAILED, space.getStatus());
    }

    @Test
    void executeProjectSpaceReindexDeletesOldIndex() throws Exception {
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.index(any(Path.class), any(Path.class), anyLong()))
                .thenReturn(CodeGraphToolResult.success("索引完成"));

        // 使用临时目录验证删除逻辑
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("cg-test-reindex");
        java.nio.file.Path indexPath = tempDir.resolve(".codegraph");
        java.nio.file.Files.createDirectories(indexPath);

        Map<String, String> context = Map.of(
                "repositoryPath", tempDir.toString(),
                "codegraphIndexPath", indexPath.toString(),
                "projectSpaceId", "1",
                "isReindex", "true"
        );

        definition.execute(context, progress);

        assertFalse(java.nio.file.Files.exists(indexPath), "旧索引目录应被删除");
        verify(codeGraphClient).index(
                eq(Path.of(tempDir.toString())),
                eq(Path.of(indexPath.toString())),
                eq(1L));

        // 清理
        FileUtil_deleteDirectoryIfExists(tempDir);
    }

    // ---- 仓库级索引 ----

    @Test
    void executeRepositoryIndexSuccess() throws Exception {
        CodeRepository entity = new CodeRepository();
        entity.setId(2L);
        when(codeRepositoryJpaRepository.findById(2L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.index(any(Path.class), anyLong()))
                .thenReturn(CodeGraphToolResult.success("索引完成"));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/baz",
                "repositoryId", "2"
        );

        definition.execute(context, progress);

        verify(indexProgressTracker).start(2L);
        verify(codeGraphClient).index(Path.of("/tmp/repos/baz"), 2L);
        verify(progress).update(100, "索引完成");
        verify(codeRepositoryJpaRepository).save(entity);
        assertEquals(RepositoryStatus.READY, entity.getStatus());
        assertNotNull(entity.getLastIndexedAt());
    }

    @Test
    void executeRepositoryIndexFailureResult() {
        CodeRepository entity = new CodeRepository();
        entity.setId(2L);
        when(codeRepositoryJpaRepository.findById(2L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.index(any(Path.class), anyLong()))
                .thenReturn(CodeGraphToolResult.error("仓库格式不支持"));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/baz",
                "repositoryId", "2"
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> definition.execute(context, progress));
        assertTrue(ex.getMessage().startsWith("CodeGraph 索引失败"));

        verify(indexProgressTracker).start(2L);
        verify(progress).update(0, "索引失败");
        verify(codeRepositoryJpaRepository).save(entity);
        assertEquals(RepositoryStatus.FAILED, entity.getStatus());
        assertEquals("仓库格式不支持", entity.getLastIndexError());
    }

    @Test
    void executeRepositoryIndexClientThrowsException() {
        CodeRepository entity = new CodeRepository();
        entity.setId(2L);
        when(codeRepositoryJpaRepository.findById(2L)).thenReturn(Optional.of(entity));
        when(codeRepositoryJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.index(any(Path.class), anyLong()))
                .thenThrow(new RuntimeException("OOM"));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/baz",
                "repositoryId", "2"
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> definition.execute(context, progress));
        assertEquals("OOM", ex.getMessage());

        verify(indexProgressTracker).start(2L);
        verify(codeRepositoryJpaRepository).save(entity);
        assertEquals(RepositoryStatus.FAILED, entity.getStatus());
    }

    // ---- 上下文校验 ----

    @Test
    void executeWithoutProjectSpaceIdOrRepositoryIdThrows() {
        Map<String, String> context = Map.of("repositoryPath", "/tmp/repos/baz");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> definition.execute(context, progress));
        assertTrue(ex.getMessage().contains("projectSpaceId"));
        assertTrue(ex.getMessage().contains("repositoryId"));
    }

    // ---- 序列化 ----

    @Test
    void serializeAndDeserializeContextRoundTrip() {
        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "codegraphIndexPath", "/tmp/repos/bar/.codegraph",
                "projectSpaceId", "1",
                "isReindex", "true"
        );

        String json = definition.serializeContext(context);
        Map<String, String> deserialized = definition.deserializeContext(json);

        assertEquals(context, deserialized);
    }

    /** 辅助方法：删除临时目录，避免测试间残留。 */
    private static void FileUtil_deleteDirectoryIfExists(java.nio.file.Path path) {
        try {
            cn.welsione.ascoder.common.FileUtil.deleteDirectoryIfExists(path);
        } catch (Exception ignored) {
            // 测试清理，忽略
        }
    }
}
