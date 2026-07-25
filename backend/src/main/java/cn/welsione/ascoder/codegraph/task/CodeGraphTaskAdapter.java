package cn.welsione.ascoder.codegraph.task;

import cn.welsione.ascoder.codegraph.infrastructure.cli.IndexProgressTracker;
import cn.welsione.ascoder.common.task.TaskEngine;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskSubmitRequest;
import cn.welsione.ascoder.repository.CodeGraphTaskPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * {@link CodeGraphTaskPort} 的 codegraph 模块实现。
 *
 * <p>将 repository 模块传入的原始参数封装为 {@link CodeGraphIndexContext} / {@link CodeGraphSyncContext}，
 * 提交到 {@link TaskEngine}。使 repository 模块无需感知 codegraph 的任务上下文类型。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeGraphTaskAdapter implements CodeGraphTaskPort {

    private final TaskEngine taskEngine;
    private final IndexProgressTracker indexProgressTracker;

    @Override
    public void submitRepositoryIndex(Path repositoryPath, Long repositoryId) {
        CodeGraphIndexContext context = new CodeGraphIndexContext(
                repositoryPath.toString(), null, false, null, repositoryId
        );
        submitIndex(context, repositoryId);
        log.info("已提交 CodeGraph 仓库索引异步任务，repositoryId={}", repositoryId);
    }

    @Override
    public void submitProjectSpaceIndex(Path repositoryPath, Path codegraphIndexPath,
                                         Long projectSpaceId, boolean reindex) {
        CodeGraphIndexContext context = new CodeGraphIndexContext(
                repositoryPath.toString(),
                codegraphIndexPath.toString(),
                reindex, projectSpaceId, null
        );
        submitIndex(context, projectSpaceId);
        log.info("已提交 CodeGraph {} 异步任务，projectSpaceId={}",
                reindex ? "重新索引" : "全量索引", projectSpaceId);
    }

    @Override
    public void submitProjectSpaceSync(Path repositoryPath, Long projectSpaceId) {
        CodeGraphSyncContext context = new CodeGraphSyncContext(repositoryPath.toString(), projectSpaceId);
        TaskSubmitRequest<CodeGraphSyncContext> request = new TaskSubmitRequest<>();
        request.setKind(TaskKind.CODEGRAPH_SYNC);
        request.setContext(context);
        request.setBusinessId(projectSpaceId);
        taskEngine.submit(request);
        log.info("已提交 CodeGraph 增量同步异步任务，projectSpaceId={}", projectSpaceId);
    }

    @Override
    public void startProgress(Long projectSpaceId) {
        indexProgressTracker.start(projectSpaceId);
    }

    @Override
    public IndexProgress getProgress(Long projectSpaceId) {
        IndexProgressTracker.IndexProgress p = indexProgressTracker.get(projectSpaceId);
        return new IndexProgress(p.getPercent(), p.getMessage(), p.isCompleted());
    }

    private void submitIndex(CodeGraphIndexContext context, Long businessId) {
        TaskSubmitRequest<CodeGraphIndexContext> request = new TaskSubmitRequest<>();
        request.setKind(TaskKind.CODEGRAPH_INDEX);
        request.setContext(context);
        request.setBusinessId(businessId);
        taskEngine.submit(request);
    }
}
