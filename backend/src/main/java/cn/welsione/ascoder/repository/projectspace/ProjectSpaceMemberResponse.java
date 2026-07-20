package cn.welsione.ascoder.repository.projectspace;

import cn.welsione.ascoder.repository.RepositoryBranchSourceKind;
import lombok.Value;

import java.util.Date;
import java.util.List;

/**
 * 项目空间成员响应 DTO。
 */
@Value
public class ProjectSpaceMemberResponse {
    Long id;
    Long projectSpaceId;
    String projectSpaceName;
    Long repositoryId;
    String repositoryName;
    Long branchWorkspaceId;
    Long branchId;
    String branchName;
    String branchRefName;
    RepositoryBranchSourceKind branchSourceKind;
    String alias;
    String role;
    String commitSha;
    String commitMessage;
    String remoteCommitSha;
    String remoteCommitMessage;
    boolean behindRemote;
    String linkPath;
    String worktreePath;
    List<ProjectSpaceCommitResponse> recentCommits;
    Date createdAt;
    Date updatedAt;

    public static ProjectSpaceMemberResponse from(ProjectSpaceMember member) {
        return from(member, List.of());
    }

    public static ProjectSpaceMemberResponse from(
            ProjectSpaceMember member,
            List<ProjectSpaceCommitResponse> recentCommits
    ) {
        return from(member, member.getCommitMessage(), null, null, false, recentCommits);
    }

    public static ProjectSpaceMemberResponse from(
            ProjectSpaceMember member,
            String commitMessage,
            String remoteCommitSha,
            String remoteCommitMessage,
            boolean behindRemote,
            List<ProjectSpaceCommitResponse> recentCommits
    ) {
        return new ProjectSpaceMemberResponse(
                member.getId(),
                member.getProjectSpaceId(),
                member.getProjectSpaceName(),
                member.getRepositoryId(),
                member.getRepositoryName(),
                member.getBranchWorkspaceId(),
                member.getBranchId(),
                member.getBranchName(),
                member.getBranchRefName(),
                member.getBranchSourceKind(),
                member.getAlias(),
                member.getRole(),
                member.getCommitSha(),
                commitMessage,
                remoteCommitSha,
                remoteCommitMessage,
                behindRemote,
                member.getLinkPath(),
                member.getWorktreePath(),
                recentCommits,
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
