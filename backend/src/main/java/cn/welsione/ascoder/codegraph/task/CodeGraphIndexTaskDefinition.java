package cn.welsione.ascoder.codegraph.task;

import cn.welsione.ascoder.codegraph.infrastructure.cli.IndexProgressTracker;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.FileUtil;
import cn.welsione.ascoder.common.task.TaskDefinition;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
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
 * CodeGraph 全量索引异步任务定义，负责执行代码图索引并同步进度到任务引擎。
 *
 * <p>支持两种索引场景：</p>
 * <ul>
 *   <li>项目空间级索引：上下文包含 projectSpaceId，索引完成后更新 ProjectSpace 状态</li>
 *   <li>仓库级索引：上下文包含 repositoryId，索引完成后更新 CodeRepository 状态</li>
 * </ul>
 *
 * <p>通用字段：repositoryPath、codegraphIndexPath（可选）、isReindex。
 * 若 isReindex 为 "true"，先删除旧 .codegraph 目录再执行全量索引。
 * 进度通过后台线程从 IndexProgressTracker 定期同步到 TaskProgress。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeGraphIndexTaskDefinition implements TaskDefinition<Map<String, String>> {

    private static final TypeReference<Map<String, String>> CONTEXT_TYPE = new TypeReference<>() {};
    private static final long PROGRESS_SYNC_INTERVAL_MS = 1000;

    private final CodeGraphClient codeGraphClient;
    private final IndexProgressTracker indexProgressTracker;
    private final ProjectSpaceJpaRepository projectSpaceJpaRepository;
    private final CodeRepositoryJpaRepository codeRepositoryJpaRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public TaskKind kind() {
        return TaskKind.CODEGRAPH_INDEX;
    }

    @Override
    public void execute(Map<String, String> context, TaskProgress progress) throws Exception {
        String repositoryPath = context.get("repositoryPath");
        String codegraphIndexPath = context.get("codegraphIndexPath");
        boolean isReindex = "true".equals(context.get("isReindex"));

        // 判断索引场景：projectSpaceId 或 repositoryId
        String projectSpaceIdStr = context.get("projectSpaceId");
        String repositoryIdStr = context.get("repositoryId");
        boolean isProjectSpaceIndex = projectSpaceIdStr != null && !projectSpaceIdStr.isBlank();

        if (isProjectSpaceIndex) {
            executeProjectSpaceIndex(context, repositoryPath, codegraphIndexPath,
                    Long.valueOf(projectSpaceIdStr), isReindex, progress);
        } else if (repositoryIdStr != null && !repositoryIdStr.isBlank()) {
            executeRepositoryIndex(repositoryPath, Long.valueOf(repositoryIdStr), isReindex, progress);
        } else {
            throw new IllegalStateException("任务上下文必须包含 projectSpaceId 或 repositoryId");
        }
    }

    /**
     * 项目空间级索引，索引完成后更新 ProjectSpace 状态。
     */
    private void executeProjectSpaceIndex(Map<String, String> context, String repositoryPath,
                                          String codegraphIndexPath, Long projectSpaceId,
                                          boolean isReindex, TaskProgress progress) throws Exception {
        log.info("开始 CodeGraph 索引任务，projectSpaceId={}，isReindex={}，path={}",
                projectSpaceId, isReindex, repositoryPath);

        // 重新索引时先删除旧索引目录
        if (isReindex) {
            log.info("删除旧索引目录，path={}", codegraphIndexPath);
            FileUtil.deleteDirectoryIfExists(Path.of(codegraphIndexPath));
        }

        // 启动进度同步线程
        Thread progressSync = startProgressSyncThread(projectSpaceId, "index-progress-sync-", progress);

        try {
            CodeGraphToolResult result;
            if (codegraphIndexPath != null && !codegraphIndexPath.isBlank()) {
                result = codeGraphClient.index(
                        Path.of(repositoryPath), Path.of(codegraphIndexPath), projectSpaceId);
            } else {
                result = codeGraphClient.index(Path.of(repositoryPath), projectSpaceId);
            }

            if (result.isSuccess()) {
                indexProgressTracker.complete(projectSpaceId);
                progress.update(100, "索引完成");

                transactionTemplate.executeWithoutResult(status -> {
                    ProjectSpace space = projectSpaceJpaRepository.findById(projectSpaceId)
                            .orElseThrow(() -> new IllegalStateException("项目空间不存在，id=" + projectSpaceId));
                    space.indexed(new Date());
                    projectSpaceJpaRepository.save(space);
                });

                log.info("CodeGraph 索引任务完成，projectSpaceId={}", projectSpaceId);
            } else {
                handleProjectSpaceIndexFailure(projectSpaceId, result.getOutput(), progress);
                throw new RuntimeException("CodeGraph 索引失败：" + result.getOutput());
            }
        } catch (RuntimeException ex) {
            if (!ex.getMessage().startsWith("CodeGraph 索引失败")) {
                handleProjectSpaceIndexFailure(projectSpaceId, ex.getMessage(), progress);
                log.error("CodeGraph 索引任务异常，projectSpaceId={}", projectSpaceId, ex);
            }
            throw ex;
        } finally {
            progressSync.interrupt();
        }
    }

    /**
     * 仓库级索引，索引完成后更新 CodeRepository 状态。
     */
    private void executeRepositoryIndex(String repositoryPath, Long repositoryId,
                                        boolean isReindex, TaskProgress progress) throws Exception {
        log.info("开始 CodeGraph 仓库索引任务，repositoryId={}，isReindex={}，path={}",
                repositoryId, isReindex, repositoryPath);

        // 重新索引时先删除旧索引目录
        if (isReindex) {
            Path indexPath = Path.of(repositoryPath).resolve(".codegraph");
            log.info("删除旧索引目录，path={}", indexPath);
            FileUtil.deleteDirectoryIfExists(indexPath);
        }

        try {
            CodeGraphToolResult result = codeGraphClient.index(Path.of(repositoryPath));

            if (result.isSuccess()) {
                progress.update(100, "索引完成");

                transactionTemplate.executeWithoutResult(status -> {
                    CodeRepository entity = codeRepositoryJpaRepository.findById(repositoryId)
                            .orElseThrow(() -> new IllegalStateException("仓库不存在，id=" + repositoryId));
                    entity.ready(new Date());
                    codeRepositoryJpaRepository.save(entity);
                });

                log.info("CodeGraph 仓库索引任务完成，repositoryId={}", repositoryId);
            } else {
                String errorMessage = result.getOutput();
                progress.update(0, "索引失败");

                transactionTemplate.executeWithoutResult(status -> {
                    CodeRepository entity = codeRepositoryJpaRepository.findById(repositoryId)
                            .orElseThrow(() -> new IllegalStateException("仓库不存在，id=" + repositoryId));
                    entity.fail(errorMessage);
                    codeRepositoryJpaRepository.save(entity);
                });

                log.error("CodeGraph 仓库索引任务失败，repositoryId={}，错误={}", repositoryId, errorMessage);
                throw new RuntimeException("CodeGraph 索引失败：" + errorMessage);
            }
        } catch (RuntimeException ex) {
            if (!ex.getMessage().startsWith("CodeGraph 索引失败")) {
                progress.update(0, "索引失败");

                transactionTemplate.executeWithoutResult(status -> {
                    CodeRepository entity = codeRepositoryJpaRepository.findById(repositoryId)
                            .orElseThrow(() -> new IllegalStateException("仓库不存在，id=" + repositoryId));
                    entity.fail(ex.getMessage());
                    codeRepositoryJpaRepository.save(entity);
                });

                log.error("CodeGraph 仓库索引任务异常，repositoryId={}", repositoryId, ex);
            }
            throw ex;
        }
    }

    private void handleProjectSpaceIndexFailure(Long projectSpaceId, String errorMessage, TaskProgress progress) {
        indexProgressTracker.fail(projectSpaceId, errorMessage);
        progress.update(0, "索引失败");

        transactionTemplate.executeWithoutResult(status -> {
            ProjectSpace space = projectSpaceJpaRepository.findById(projectSpaceId)
                    .orElseThrow(() -> new IllegalStateException("项目空间不存在，id=" + projectSpaceId));
            space.fail(errorMessage);
            projectSpaceJpaRepository.save(space);
        });
    }

    /**
     * 启动进度同步线程，定期从 IndexProgressTracker 读取进度并同步到 TaskProgress。
     */
    private Thread startProgressSyncThread(Long projectSpaceId, String threadNamePrefix, TaskProgress progress) {
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
        }, threadNamePrefix + projectSpaceId);
        progressSync.setDaemon(true);
        progressSync.start();
        return progressSync;
    }

    @Override
    public String serializeContext(Map<String, String> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 CodeGraph 索引任务上下文失败", e);
        }
    }

    @Override
    public Map<String, String> deserializeContext(String json) {
        try {
            return objectMapper.readValue(json, CONTEXT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 CodeGraph 索引任务上下文失败", e);
        }
    }
}
