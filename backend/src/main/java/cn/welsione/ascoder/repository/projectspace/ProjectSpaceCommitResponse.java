package cn.welsione.ascoder.repository.projectspace;

import cn.welsione.ascoder.repository.git.GitCommitInfo;
import lombok.Value;

/**
 * 项目空间成员提交历史响应 DTO。
 */
@Value
public class ProjectSpaceCommitResponse {
    String commitSha;
    String shortSha;
    String commitMessage;
    String committedAt;

    public static ProjectSpaceCommitResponse from(GitCommitInfo commit) {
        return new ProjectSpaceCommitResponse(
                commit.getCommitSha(),
                commit.getShortSha(),
                commit.getCommitMessage(),
                commit.getCommittedAt()
        );
    }
}
