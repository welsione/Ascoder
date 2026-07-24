package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BranchRefreshTaskDefinition 单元测试，覆盖分支刷新执行、进度更新、业务标签解析和序列化。
 */
class BranchRefreshTaskDefinitionTests {

    private RepositoryBranchService repositoryBranchService;
    private CodeRepositoryJpaRepository codeRepositoryJpaRepository;
    private ObjectMapper objectMapper;
    private BranchRefreshTaskDefinition definition;
    private TaskProgress progress;

    @BeforeEach
    void setUp() {
        repositoryBranchService = mock(RepositoryBranchService.class);
        codeRepositoryJpaRepository = mock(CodeRepositoryJpaRepository.class);
        objectMapper = new ObjectMapper();
        progress = mock(TaskProgress.class);

        when(progress.isCancelled()).thenReturn(false);

        definition = new BranchRefreshTaskDefinition(repositoryBranchService, codeRepositoryJpaRepository, objectMapper);
    }

    @Test
    void kindReturnsBranchRefresh() {
        assertEquals(TaskKind.BRANCH_REFRESH, definition.kind());
    }

    @Test
    void executeCallsRefresh() throws Exception {
        Map<String, String> context = Map.of("repositoryId", "42");

        definition.execute(context, progress);

        verify(repositoryBranchService).refresh(eq(42L), any());
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

    @Test
    void resolveBusinessLabelReturnsRepoName() {
        CodeRepository repo = mock(CodeRepository.class);
        when(repo.getName()).thenReturn("ascoder");
        when(codeRepositoryJpaRepository.findById(42L)).thenReturn(Optional.of(repo));

        assertEquals("ascoder (仓库)", definition.resolveBusinessLabel(42L));
    }

    @Test
    void resolveBusinessLabelReturnsNullWhenNotFound() {
        when(codeRepositoryJpaRepository.findById(99L)).thenReturn(Optional.empty());

        assertNull(definition.resolveBusinessLabel(99L));
    }
}
