package cn.welsione.ascoder.repository.git;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Git 提交信息快照，用于向前端展示真实提交历史。
 */
@Value
@AllArgsConstructor
public class GitCommitInfo {
    String commitSha;
    String shortSha;
    String commitMessage;
    String committedAt;
}
