package cn.welsione.ascoder.repository.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Git clone 异步任务上下文，携带远程 URL、目标路径和可选凭据。
 *
 * <p>由 {@link GitCloneTaskDefinition} 序列化/反序列化。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitCloneContext {
    /** 远程仓库 URL。 */
    private String remoteUrl;
    /** 本地目标路径。 */
    private String targetPath;
    /** 克隆的分支名（可选，为空时使用默认分支）。 */
    private String branchName;
    /** 仓库 ID。 */
    private Long repositoryId;
    /** 认证用户名（可选）。 */
    private String authUsername;
    /** 认证密码（可选）。 */
    private String authPassword;
}
