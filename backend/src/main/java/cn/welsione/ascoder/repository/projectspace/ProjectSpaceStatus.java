package cn.welsione.ascoder.repository.projectspace;

/** 项目空间的生命周期状态，覆盖创建、准备、就绪索引、索引中、就绪、过期与失败。 */
public enum ProjectSpaceStatus {
    CREATED,
    PREPARING,
    READY_TO_INDEX,
    INDEXING,
    READY,
    STALE,
    FAILED
}
