package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BranchRefreshTaskDefinition 单元测试，覆盖分支刷新执行、进度更新和序列化。
 */
class BranchRefreshTaskDefinitionTests {

    private RepositoryBranchService repositoryBranchService;
    private ObjectMapper objectMapper;
    private BranchRefreshTaskDefinition definition;
    private TaskProgress progress;

    @BeforeEach
    void setUp() {
        repositoryBranchService = mock(RepositoryBranchService.class);
        objectMapper = new ObjectMapper();
        progress = mock(TaskProgress.class);

        when(progress.isCancelled()).thenReturn(false);

        definition = new BranchRefreshTaskDefinition(repositoryBranchService, objectMapper);
    }

    @Test
    void kindReturnsBranchRefresh() {
        assertEquals(TaskKind.BRANCH_REFRESH, definition.kind());
    }

    @Test
    void executeCallsRefresh() throws Exception {
        Map<String, String> context = Map.of("repositoryId", "42");

        definition.execute(context, progress);

        verify(repositoryBranchService).refresh(42L);
    }

    @Test
    void executeUpdatesProgressTo100() throws Exception {
        Map<String, String> context = Map.of("repositoryId", "42");

        definition.execute(context, progress);

        verify(progress).update(100, "分支刷新完成");
    }

    @Test
    void serializeAndDeserializeContextRoundTrip() {
        Map<String, String> context = Map.of("repositoryId", "42");

        String json = definition.serializeContext(context);
        Map<String, String> deserialized = definition.deserializeContext(json);

        assertEquals(context, deserialized);
    }
}
