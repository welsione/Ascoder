package cn.welsione.ascoder.repository;

import lombok.Value;

import java.util.Date;

/**
 * 仓库分支引用响应 DTO。
 */
@Value
public class RepositoryBranchResponse {
    Long id;
    Long repositoryId;
    String repositoryName;
    String name;
    String refName;
    String commitSha;
    String remoteName;
    RepositoryBranchSourceKind sourceKind;
    boolean active;
    Date lastSeenAt;
    Date createdAt;
    Date updatedAt;

    public static RepositoryBranchResponse from(RepositoryBranch branch) {
        return new RepositoryBranchResponse(
                branch.getId(),
                branch.getRepositoryId(),
                branch.getRepositoryName(),
                branch.getName(),
                branch.getRefName(),
                branch.getCommitSha(),
                branch.getRemoteName(),
                branch.getSourceKind(),
                branch.isActive(),
                branch.getLastSeenAt(),
                branch.getCreatedAt(),
                branch.getUpdatedAt()
        );
    }
}
