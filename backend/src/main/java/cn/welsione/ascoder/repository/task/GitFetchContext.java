package cn.welsione.ascoder.repository.task;

import cn.welsione.ascoder.repository.GitSyncOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Git fetch/pull 异步任务上下文，携带仓库路径、操作类型和可选凭据。
 *
 * <p>由 {@link GitFetchTaskDefinition} 序列化/反序列化。当 {@link #projectSpaceId} 非空时，
 * 表示由项目空间拉取触发，fetch 完成后会发布事件通知项目空间刷新。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitFetchContext {
    /** 仓库本地路径。 */
    private String repositoryPath;
    /** 仓库 ID。 */
    private Long repositoryId;
    /** 同步操作类型。 */
    private GitSyncOperation operation;
    /** 认证用户名（可选）。 */
    private String authUsername;
    /** 认证密码（可选）。 */
    private String authPassword;
    /** 远程仓库 URL（可选）。 */
    private String remoteUrl;
    /** 项目空间 ID（可选，非空表示由项目空间拉取触发）。 */
    private Long projectSpaceId;
}
