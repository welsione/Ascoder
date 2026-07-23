package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskDefinition;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import cn.welsione.ascoder.repository.git.GitCredentialStore;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
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
 * Git fetch/pull 异步任务定义，负责同步远程仓库并刷新分支信息。
 *
 * <p>上下文包含 repositoryPath、repositoryId、operation 三个字段，
 * operation 取值为 "fetch" 或 "pull"。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitFetchTaskDefinition implements TaskDefinition<Map<String, String>> {

    private static final TypeReference<Map<String, String>> CONTEXT_TYPE = new TypeReference<>() {};

    private final GitRepositoryService gitRepositoryService;
    private final GitCredentialStore gitCredentialStore;
    private final RepositoryBranchService repositoryBranchService;
    private final CodeRepositoryJpaRepository codeRepositoryJpaRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public TaskKind kind() {
        return TaskKind.GIT_FETCH;
    }

    @Override
    public void execute(Map<String, String> context, TaskProgress progress) throws Exception {
        String repositoryPath = context.get("repositoryPath");
        Long repositoryId = Long.valueOf(context.get("repositoryId"));
        String operation = context.get("operation");

        log.info("开始同步仓库，repositoryId={}，operation={}，path={}", repositoryId, operation, repositoryPath);

        // 写入凭据
        String authUsername = context.get("authUsername");
        String authPassword = context.get("authPassword");
        if (authUsername != null && !authUsername.isBlank()
                && authPassword != null && !authPassword.isBlank()) {
            String remoteUrl = context.get("remoteUrl");
            if (remoteUrl != null && !remoteUrl.isBlank()) {
                gitCredentialStore.upsert(remoteUrl, authUsername, authPassword);
            }
        }

        String errorMessage = null;
        try {
            Path path = Path.of(repositoryPath);
            if ("pull".equals(operation)) {
                gitRepositoryService.pull(path);
            } else {
                gitRepositoryService.fetch(path);
            }
            log.info("仓库同步完成，repositoryId={}，operation={}", repositoryId, operation);
        } catch (RuntimeException ex) {
            errorMessage = ex.getMessage();
            log.warn("仓库同步失败，repositoryId={}，operation={}：{}", repositoryId, operation, errorMessage);
        }

        progress.update(50, "同步完成，正在刷新分支...");
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

        progress.update(100, "完成");
        log.info("Git fetch 任务完成，repositoryId={}", repositoryId);
    }

    @Override
    public String serializeContext(Map<String, String> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 Git fetch 任务上下文失败", e);
        }
    }

    @Override
    public Map<String, String> deserializeContext(String json) {
        try {
            return objectMapper.readValue(json, CONTEXT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 Git fetch 任务上下文失败", e);
        }
    }
}
