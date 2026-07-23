package cn.welsione.ascoder.codegraph.task;

import cn.welsione.ascoder.codegraph.infrastructure.cli.IndexProgressTracker;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.task.TaskDefinition;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.Date;
import java.util.Map;

/**
 * CodeGraph 增量同步异步任务定义，负责执行代码图增量同步并同步进度到任务引擎。
 *
 * <p>上下文包含 repositoryPath、projectSpaceId 两个字段。
 * 进度通过后台线程从 IndexProgressTracker 定期同步到 TaskProgress。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeGraphSyncTaskDefinition implements TaskDefinition<Map<String, String>> {

    private static final TypeReference<Map<String, String>> CONTEXT_TYPE = new TypeReference<>() {};
    private static final long PROGRESS_SYNC_INTERVAL_MS = 1000;

    private final CodeGraphClient codeGraphClient;
    private final IndexProgressTracker indexProgressTracker;
    private final ProjectSpaceJpaRepository projectSpaceJpaRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public TaskKind kind() {
        return TaskKind.CODEGRAPH_SYNC;
    }

    @Override
    public void execute(Map<String, String> context, TaskProgress progress) throws Exception {
        String repositoryPath = context.get("repositoryPath");
        Long projectSpaceId = Long.valueOf(context.get("projectSpaceId"));

        log.info("开始 CodeGraph 增量同步任务，projectSpaceId={}，path={}", projectSpaceId, repositoryPath);

        // 启动进度同步线程，定期从 IndexProgressTracker 读取进度并同步到 TaskProgress
        Thread progressSync = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    IndexProgressTracker.IndexProgress p = indexProgressTracker.get(projectSpaceId);
                    if (p != null && p.getPercent() >= 0) {
                        progress.update(p.getPercent(), p.getMessage());
                    }
                    Thread.sleep(PROGRESS_SYNC_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "sync-progress-sync-" + projectSpaceId);
        progressSync.setDaemon(true);
        progressSync.start();

        try {
            CodeGraphToolResult result = codeGraphClient.sync(Path.of(repositoryPath), projectSpaceId);

            if (result.isSuccess()) {
                indexProgressTracker.complete(projectSpaceId);
                progress.update(100, "同步完成");

                transactionTemplate.executeWithoutResult(status -> {
                    ProjectSpace space = projectSpaceJpaRepository.findById(projectSpaceId)
                            .orElseThrow(() -> new IllegalStateException("项目空间不存在，id=" + projectSpaceId));
                    space.indexed(new Date());
                    projectSpaceJpaRepository.save(space);
                });

                log.info("CodeGraph 增量同步任务完成，projectSpaceId={}", projectSpaceId);
            } else {
                String errorMessage = result.getOutput();
                indexProgressTracker.fail(projectSpaceId, errorMessage);
                progress.update(0, "同步失败");

                transactionTemplate.executeWithoutResult(status -> {
                    ProjectSpace space = projectSpaceJpaRepository.findById(projectSpaceId)
                            .orElseThrow(() -> new IllegalStateException("项目空间不存在，id=" + projectSpaceId));
                    space.fail(errorMessage);
                    projectSpaceJpaRepository.save(space);
                });

                log.error("CodeGraph 增量同步任务失败，projectSpaceId={}，错误={}", projectSpaceId, errorMessage);
                throw new RuntimeException("CodeGraph 增量同步失败：" + errorMessage);
            }
        } catch (RuntimeException ex) {
            if (!ex.getMessage().startsWith("CodeGraph 增量同步失败")) {
                indexProgressTracker.fail(projectSpaceId, ex.getMessage());
                progress.update(0, "同步失败");

                transactionTemplate.executeWithoutResult(status -> {
                    ProjectSpace space = projectSpaceJpaRepository.findById(projectSpaceId)
                            .orElseThrow(() -> new IllegalStateException("项目空间不存在，id=" + projectSpaceId));
                    space.fail(ex.getMessage());
                    projectSpaceJpaRepository.save(space);
                });

                log.error("CodeGraph 增量同步任务异常，projectSpaceId={}", projectSpaceId, ex);
            }
            throw ex;
        } finally {
            progressSync.interrupt();
        }
    }

    @Override
    public String serializeContext(Map<String, String> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 CodeGraph 同步任务上下文失败", e);
        }
    }

    @Override
    public Map<String, String> deserializeContext(String json) {
        try {
            return objectMapper.readValue(json, CONTEXT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 CodeGraph 同步任务上下文失败", e);
        }
    }
}
