package cn.welsione.ascoder.repository.projectspace.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目空间准备异步任务上下文，携带项目空间 ID 及准备模式。
 *
 * <p>由 {@link ProjectSpacePrepareTaskDefinition} 序列化/反序列化。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSpacePrepareContext {
    /** 项目空间 ID。 */
    Long projectSpaceId;

    /**
     * 是否将各成员 worktree 推进到远端最新 commit。
     *
     * <p>false（默认，创建场景）：worktree checkout 到 {@code member.commitSha}（首次准备时为 null，
     * 由 prepare 读本地分支 commit）。true（拉取更新场景）：worktree checkout 到
     * {@code origin/<branch>} 最新 commit，使落后远端的成员同步到远端。</p>
     */
    boolean advanceToRemote;
}
