package cn.welsione.ascoder.repository.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分支刷新异步任务上下文，仅携带仓库 ID。
 *
 * <p>由 {@link BranchRefreshTaskDefinition} 序列化/反序列化。凭据在任务提交前
 * 已写入 credential store，任务执行时直接读取，无需在上下文中传递。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchRefreshContext {
    /** 仓库 ID。 */
    private Long repositoryId;
}
