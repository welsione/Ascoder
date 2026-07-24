package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.common.task.TaskDefinition;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import cn.welsione.ascoder.repository.git.GitProgressMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 分支刷新异步任务定义，负责刷新指定仓库的分支列表。
 *
 * <p>上下文仅包含 repositoryId 字段。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BranchRefreshTaskDefinition implements TaskDefinition<Map<String, String>> {

    private static final TypeReference<Map<String, String>> CONTEXT_TYPE = new TypeReference<>() {};

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
    public void execute(Map<String, String> context, TaskProgress progress) throws Exception {
        Long repositoryId = Long.valueOf(context.get("repositoryId"));

        log.info("开始刷新分支，repositoryId={}", repositoryId);

        progress.update(0, "正在 fetch 远程引用...");
        repositoryBranchService.refresh(repositoryId, new GitProgressMapper(progress, 0, 90)::onLine);

        progress.update(100, "分支刷新完成");
        log.info("分支刷新完成，repositoryId={}", repositoryId);
    }

    @Override
    public String serializeContext(Map<String, String> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("序列化分支刷新任务上下文失败", e);
        }
    }

    @Override
    public Map<String, String> deserializeContext(String json) {
        try {
            return objectMapper.readValue(json, CONTEXT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化分支刷新任务上下文失败", e);
        }
    }
}
