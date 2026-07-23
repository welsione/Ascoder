package cn.welsione.ascoder.common.task;

/**
 * 异步任务类型，每种类型对应独立的线程池和限流配置。
 */
public enum TaskKind {
    /** Git clone 仓库 */
    GIT_CLONE,
    /** Git fetch / pull */
    GIT_FETCH,
    /** CodeGraph 全量索引 */
    CODEGRAPH_INDEX,
    /** CodeGraph 增量同步 */
    CODEGRAPH_SYNC,
    /** 项目空间准备（含 clone + fetch + checkout） */
    PROJECT_SPACE_PREPARE,
    /** 分支列表刷新 */
    BRANCH_REFRESH
}
