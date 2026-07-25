package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskCancelledException;
import cn.welsione.ascoder.common.task.TaskDefinition;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import cn.welsione.ascoder.repository.git.GitCredentialStore;
import cn.welsione.ascoder.repository.git.GitProgressMapper;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.Date;

/**
 * Git clone 异步任务定义，负责克隆远程仓库并刷新分支信息。
 *
 * <p>上下文为 {@link GitCloneContext}，通过 Jackson ObjectMapper 序列化/反序列化。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitCloneTaskDefinition implements TaskDefinition<GitCloneContext> {

    private final GitRepositoryService gitRepositoryService;
    private final GitCredentialStore gitCredentialStore;
    private final RepositoryBranchService repositoryBranchService;
    private final CodeRepositoryJpaRepository codeRepositoryJpaRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public TaskKind kind() {
        return TaskKind.GIT_CLONE;
    }

    @Override
    public long defaultTimeoutMs() {
        return 60 * 60 * 1000L; // 1 小时（大仓库 clone 可能较慢）
    }

    @Override
    public String resolveBusinessLabel(Long businessId) {
        return codeRepositoryJpaRepository.findById(businessId)
                .map(repo -> repo.getName() + " (仓库)")
                .orElse(null);
    }

    @Override
    public void execute(GitCloneContext context, TaskProgress progress) throws Exception {
        String remoteUrl = context.getRemoteUrl();
        String targetPath = context.getTargetPath();
        String branchName = context.getBranchName();
        Long repositoryId = context.getRepositoryId();

        log.info("开始克隆仓库，remoteUrl={}，targetPath={}，branchName={}，repositoryId={}",
                remoteUrl, targetPath, branchName, repositoryId);

        // 写入凭据
        String authUsername = context.getAuthUsername();
        String authPassword = context.getAuthPassword();
        if (authUsername != null && !authUsername.isBlank()
                && authPassword != null && !authPassword.isBlank()) {
            gitCredentialStore.upsert(remoteUrl, authUsername, authPassword);
        }

        gitRepositoryService.cloneRepository(remoteUrl, Path.of(targetPath), branchName,
                new GitProgressMapper(progress, 0, 80)::onLine);
        log.info("仓库克隆完成，repositoryId={}", repositoryId);

        progress.update(80, "克隆完成，正在刷新分支...");
        progress.checkCancelled();

        repositoryBranchService.refresh(repositoryId);

        // 推断 defaultBranch
        String defaultBranch = branchName;
        if (defaultBranch == null || defaultBranch.isBlank()) {
            try {
                defaultBranch = gitRepositoryService.currentBranch(Path.of(targetPath));
            } catch (RuntimeException ex) {
                log.warn("读取默认分支失败，repositoryId={}：{}", repositoryId, ex.getMessage());
            }
        }

        String finalDefaultBranch = defaultBranch;
        transactionTemplate.executeWithoutResult(status -> {
            CodeRepository entity = codeRepositoryJpaRepository.findById(repositoryId)
                    .orElseThrow(() -> new IllegalStateException("仓库不存在，id=" + repositoryId));
            entity.cloned();
            entity.pulled(new Date());
            if (finalDefaultBranch != null && !finalDefaultBranch.isBlank()) {
                entity.setDefaultBranch(finalDefaultBranch);
            }
            codeRepositoryJpaRepository.save(entity);
        });

        progress.update(100, "完成");
        log.info("Git clone 任务完成，repositoryId={}", repositoryId);
    }

    @Override
    public String serializeContext(GitCloneContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 Git clone 任务上下文失败", e);
        }
    }

    @Override
    public GitCloneContext deserializeContext(String json) {
        try {
            return objectMapper.readValue(json, GitCloneContext.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 Git clone 任务上下文失败", e);
        }
    }
}
