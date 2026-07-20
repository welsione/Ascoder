package cn.welsione.ascoder.repository.git;

import cn.welsione.ascoder.repository.RepositoryBranchSourceKind;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Git 分支引用信息，表达可被项目空间选择的结构化 ref。
 */
@Value
@AllArgsConstructor
public class GitBranchInfo {
    String branchName;
    String refName;
    String commitSha;
    String remoteName;
    RepositoryBranchSourceKind sourceKind;
}
