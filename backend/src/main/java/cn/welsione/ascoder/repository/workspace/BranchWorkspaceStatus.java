package cn.welsione.ascoder.repository.workspace;

/** 分支工作区的生命周期状态，覆盖创建、准备中、就绪、索引中、过期与失败。 */
public enum BranchWorkspaceStatus {
    CREATED,
    PREPARING,
    READY,
    INDEXING,
    STALE,
    FAILED
}
