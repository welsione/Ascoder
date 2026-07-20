package cn.welsione.ascoder.repository.workspace;

import lombok.AllArgsConstructor;
import lombok.Value;

/** Git 分支查询响应，携带分支名、提交 SHA 及其工作区状态与 ID。 */
@Value
@AllArgsConstructor
public class GitBranchResponse {
    String name;
    String commitSha;
    BranchWorkspaceStatus workspaceStatus;
    Long workspaceId;
}
