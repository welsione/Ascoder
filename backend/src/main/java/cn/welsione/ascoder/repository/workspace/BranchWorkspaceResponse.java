package cn.welsione.ascoder.repository.workspace;

import lombok.Value;

import java.util.Date;

/**
 * 分支工作区响应 DTO。
 */
@Value
public class BranchWorkspaceResponse {
    Long id;
    Long repositoryId;
    String repositoryName;
    String branchName;
    String commitSha;
    String commitMessage;
    String worktreePath;
    String codegraphIndexPath;
    BranchWorkspaceStatus status;
    Date lastIndexedAt;
    String lastIndexError;
    Date createdAt;
    Date updatedAt;

    public static BranchWorkspaceResponse from(BranchWorkspace workspace) {
        return BranchWorkspaceMapper.INSTANCE.toResponse(workspace);
    }
}
