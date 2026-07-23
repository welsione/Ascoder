package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskCancelledException;
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
 * Git clone 异步任务定义，负责克隆远程仓库并刷新分支信息。
 *
 * <p>上下文包含 remoteUrl、targetPath、branchName、repositoryId 四个字段，
 * 通过 Jackson ObjectMapper 序列化/反序列化为 Map。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitCloneTaskDefinition implements TaskDefinition<Map<String, String>> {

    private static final TypeReference<Map<String, String>> CONTEXT_TYPE = new TypeReference<>() {};

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
    public void execute(Map<String, String> context, TaskProgress progress) throws Exception {
        String remoteUrl = context.get("remoteUrl");
        String targetPath = context.get("targetPath");
        String branchName = context.get("branchName");
        Long repositoryId = Long.valueOf(context.get("repositoryId"));

        log.info("开始克隆仓库，remoteUrl={}，targetPath={}，branchName={}，repositoryId={}",
                remoteUrl, targetPath, branchName, repositoryId);

        // 写入凭据
        String authUsername = context.get("authUsername");
        String authPassword = context.get("authPassword");
        if (authUsername != null && !authUsername.isBlank()
                && authPassword != null && !authPassword.isBlank()) {
            gitCredentialStore.upsert(remoteUrl, authUsername, authPassword);
        }

        gitRepositoryService.cloneRepository(remoteUrl, Path.of(targetPath), branchName);
        log.info("仓库克隆完成，repositoryId={}", repositoryId);

        progress.update(50, "克隆完成，正在刷新分支...");
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
    public String serializeContext(Map<String, String> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 Git clone 任务上下文失败", e);
        }
    }

    @Override
    public Map<String, String> deserializeContext(String json) {
        try {
            return objectMapper.readValue(json, CONTEXT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 Git clone 任务上下文失败", e);
        }
    }
}
