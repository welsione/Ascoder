package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskDefinition;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import cn.welsione.ascoder.repository.git.GitProgressMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 分支刷新异步任务定义，负责刷新指定仓库的分支列表。
 *
 * <p>上下文为 {@link BranchRefreshContext}，仅携带 repositoryId。
 * 凭据在任务提交前已写入 credential store，任务执行时直接读取。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BranchRefreshTaskDefinition implements TaskDefinition<BranchRefreshContext> {

    private final RepositoryBranchService repositoryBranchService;
    private final CodeRepositoryJpaRepository codeRepositoryJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public TaskKind kind() {
        return TaskKind.BRANCH_REFRESH;
    }

    @Override
    public long defaultTimeoutMs() {
        return 10 * 60 * 1000L; // 10 分钟
    }

    @Override
    public String resolveBusinessLabel(Long businessId) {
        return codeRepositoryJpaRepository.findById(businessId)
                .map(repo -> repo.getName() + " (仓库)")
                .orElse(null);
    }

    @Override
    public void execute(BranchRefreshContext context, TaskProgress progress) throws Exception {
        Long repositoryId = context.getRepositoryId();

        log.info("开始刷新分支，repositoryId={}", repositoryId);

        progress.update(0, "正在 fetch 远程引用...");
        repositoryBranchService.refresh(repositoryId, new GitProgressMapper(progress, 0, 90)::onLine);

        progress.update(100, "分支刷新完成");
        log.info("分支刷新完成，repositoryId={}", repositoryId);
    }

    @Override
    public String serializeContext(BranchRefreshContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("序列化分支刷新任务上下文失败", e);
        }
    }

    @Override
    public BranchRefreshContext deserializeContext(String json) {
        try {
            return objectMapper.readValue(json, BranchRefreshContext.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化分支刷新任务上下文失败", e);
        }
    }
}
