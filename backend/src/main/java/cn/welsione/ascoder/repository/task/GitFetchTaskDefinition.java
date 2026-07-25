package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskDefinition;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.common.task.TaskContextSerializer;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.GitSyncOperation;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import cn.welsione.ascoder.repository.git.GitCredentialStore;
import cn.welsione.ascoder.repository.git.GitProgressMapper;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceFetchCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.Date;

/**
 * Git fetch/pull 异步任务定义，负责同步远程仓库并刷新分支信息。
 *
 * <p>上下文为 {@link GitFetchContext}，operation 字段决定执行 fetch 还是 pull。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitFetchTaskDefinition implements TaskDefinition<GitFetchContext> {

    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L; // 30 分钟

    /** fetch/pull 执行阶段的进度上限，后续阶段从该值继续推进。 */
    private static final int SYNC_PROGRESS_END = 80;
    private static final int COMPLETE_PROGRESS = 100;

    private final GitRepositoryService gitRepositoryService;
    private final GitCredentialStore gitCredentialStore;
    private final RepositoryBranchService repositoryBranchService;
    private final CodeRepositoryJpaRepository codeRepositoryJpaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public TaskKind kind() {
        return TaskKind.GIT_FETCH;
    }

    @Override
    public long defaultTimeoutMs() {
        return DEFAULT_TIMEOUT_MS;
    }

    @Override
    public String resolveBusinessLabel(Long businessId) {
        return codeRepositoryJpaRepository.findById(businessId)
                .map(repo -> repo.getName() + " (仓库)")
                .orElse(null);
    }

    @Override
    public void execute(GitFetchContext context, TaskProgress progress) throws Exception {
        String repositoryPath = context.getRepositoryPath();
        Long repositoryId = context.getRepositoryId();
        // 向后兼容：旧任务上下文可能不含 operation 字段，默认为 FETCH
        GitSyncOperation operation = context.getOperation() != null
                ? context.getOperation() : GitSyncOperation.FETCH;

        log.info("开始同步仓库，repositoryId={}，operation={}，path={}", repositoryId, operation, repositoryPath);

        // 写入凭据
        if (context.getAuthUsername() != null && !context.getAuthUsername().isBlank()
                && context.getAuthPassword() != null && !context.getAuthPassword().isBlank()) {
            String remoteUrl = context.getRemoteUrl();
            if (remoteUrl != null && !remoteUrl.isBlank()) {
                gitCredentialStore.upsert(remoteUrl, context.getAuthUsername(), context.getAuthPassword());
            }
        }

        String errorMessage = null;
        try {
            Path path = Path.of(repositoryPath);
            GitProgressMapper progressMapper = new GitProgressMapper(progress, 0, SYNC_PROGRESS_END);
            if (operation == GitSyncOperation.PULL) {
                gitRepositoryService.pull(path, progressMapper::onLine);
            } else {
                gitRepositoryService.fetch(path, progressMapper::onLine);
            }
            log.info("仓库同步完成，repositoryId={}，operation={}", repositoryId, operation);
        } catch (RuntimeException ex) {
            errorMessage = ex.getMessage();
            log.warn("仓库同步失败，repositoryId={}，operation={}：{}", repositoryId, operation, errorMessage);
        }

        progress.update(SYNC_PROGRESS_END, "同步完成，正在刷新分支...");
        progress.checkCancelled();

        repositoryBranchService.refresh(repositoryId);

        String finalErrorMessage = errorMessage;
        transactionTemplate.executeWithoutResult(status -> {
            CodeRepository entity = codeRepositoryJpaRepository.findById(repositoryId)
                    .orElseThrow(() -> new IllegalStateException("仓库不存在，id=" + repositoryId));
            if (finalErrorMessage != null) {
                entity.pullFailed(finalErrorMessage);
            } else {
                entity.pulled(new Date());
            }
            codeRepositoryJpaRepository.save(entity);
        });

        progress.update(COMPLETE_PROGRESS, "完成");
        log.info("Git fetch 任务完成，repositoryId={}", repositoryId);

        publishFetchCompletedIfNeeded(context);
    }

    /**
     * 若由项目空间拉取触发（上下文含 projectSpaceId），fetch 完成后发布事件通知项目空间刷新。
     */
    private void publishFetchCompletedIfNeeded(GitFetchContext context) {
        Long projectSpaceId = context.getProjectSpaceId();
        if (projectSpaceId == null) {
            return;
        }
        eventPublisher.publishEvent(new ProjectSpaceFetchCompletedEvent(projectSpaceId));
        log.info("已发布项目空间 fetch 完成事件，projectSpaceId={}", projectSpaceId);
    }

    @Override
    public String serializeContext(GitFetchContext context) {
        return TaskContextSerializer.serialize(objectMapper, context, "Git fetch");
    }

    @Override
    public GitFetchContext deserializeContext(String json) {
        return TaskContextSerializer.deserialize(objectMapper, json, GitFetchContext.class, "Git fetch");
    }
}
