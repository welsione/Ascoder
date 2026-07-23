package cn.welsione.ascoder.codegraph.task;

import cn.welsione.ascoder.codegraph.infrastructure.cli.IndexProgressTracker;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
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
 * CodeGraphSyncTaskDefinition 单元测试，覆盖增量同步成功/失败路径及序列化。
 */
class CodeGraphSyncTaskDefinitionTests {

    private CodeGraphClient codeGraphClient;
    private IndexProgressTracker indexProgressTracker;
    private ProjectSpaceJpaRepository projectSpaceJpaRepository;
    private TransactionTemplate transactionTemplate;
    private ObjectMapper objectMapper;
    private CodeGraphSyncTaskDefinition definition;
    private TaskProgress progress;

    @BeforeEach
    void setUp() {
        codeGraphClient = mock(CodeGraphClient.class);
        indexProgressTracker = mock(IndexProgressTracker.class);
        projectSpaceJpaRepository = mock(ProjectSpaceJpaRepository.class);
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

        when(indexProgressTracker.get(anyLong()))
                .thenReturn(new IndexProgressTracker.IndexProgress(0, "进行中", false));

        definition = new CodeGraphSyncTaskDefinition(
                codeGraphClient, indexProgressTracker, projectSpaceJpaRepository,
                transactionTemplate, objectMapper);
    }

    @Test
    void kindReturnsCodegraphSync() {
        assertEquals(TaskKind.CODEGRAPH_SYNC, definition.kind());
    }

    @Test
    void executeSuccess() throws Exception {
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.sync(any(Path.class), anyLong()))
                .thenReturn(CodeGraphToolResult.success("同步完成"));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "projectSpaceId", "1"
        );

        definition.execute(context, progress);

        verify(codeGraphClient).sync(Path.of("/tmp/repos/bar"), 1L);
        verify(indexProgressTracker).complete(1L);
        verify(progress).update(100, "同步完成");
        verify(projectSpaceJpaRepository).save(space);
        assertEquals(ProjectSpaceStatus.READY, space.getStatus());
        assertNotNull(space.getLastIndexedAt());
    }

    @Test
    void executeFailureResult() {
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.sync(any(Path.class), anyLong()))
                .thenReturn(CodeGraphToolResult.error("同步引擎错误"));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "projectSpaceId", "1"
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> definition.execute(context, progress));
        assertTrue(ex.getMessage().startsWith("CodeGraph 增量同步失败"));

        verify(indexProgressTracker).fail(1L, "同步引擎错误");
        verify(progress).update(0, "同步失败");
        verify(projectSpaceJpaRepository).save(space);
        assertEquals(ProjectSpaceStatus.FAILED, space.getStatus());
        assertEquals("同步引擎错误", space.getLastError());
    }

    @Test
    void executeClientThrowsException() {
        ProjectSpace space = new ProjectSpace();
        space.setId(1L);
        when(projectSpaceJpaRepository.findById(1L)).thenReturn(Optional.of(space));
        when(projectSpaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(codeGraphClient.sync(any(Path.class), anyLong()))
                .thenThrow(new RuntimeException("网络中断"));

        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "projectSpaceId", "1"
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> definition.execute(context, progress));
        assertEquals("网络中断", ex.getMessage());

        verify(indexProgressTracker).fail(1L, "网络中断");
        verify(progress).update(0, "同步失败");
        assertEquals(ProjectSpaceStatus.FAILED, space.getStatus());
    }

    @Test
    void serializeAndDeserializeContextRoundTrip() {
        Map<String, String> context = Map.of(
                "repositoryPath", "/tmp/repos/bar",
                "projectSpaceId", "1"
        );

        String json = definition.serializeContext(context);
        Map<String, String> deserialized = definition.deserializeContext(json);

        assertEquals(context, deserialized);
    }
}
