package cn.welsione.ascoder.repository;

/**
 * 仓库状态枚举。
 */
public enum RepositoryStatus {
    /** 仓库已创建（clone 完成或本地仓库）。 */
    CREATED,
    /** 正在克隆远程仓库。 */
    CLONING,
    /** 正在同步（fetch / pull）。 */
    SYNCING,
    /** 正在索引。 */
    INDEXING,
    /** 索引完成，可用。 */
    READY,
    /** 失败。 */
    FAILED
}
