package cn.welsione.ascoder.repository;

import java.nio.file.Path;

/**
 * CodeGraph 任务编排端口，由 codegraph 模块实现。
 *
 * <p>repository 模块通过该端口提交索引/同步任务并查询索引进度，
 * 避免直接依赖 codegraph 模块的任务上下文与进度跟踪器实现，
 * 消除 repository ↔ codegraph 的循环依赖。</p>
 *
 * <p>调用方传入原始参数（路径、ID、是否重新索引），
 * 由实现方在 codegraph 模块内部构造任务上下文并提交给 {@code TaskEngine}。</p>
 */
public interface CodeGraphTaskPort {

    /**
     * 提交仓库级全量索引任务。
     *
     * @param repositoryPath 仓库本地路径
     * @param repositoryId   仓库 ID，作为任务 businessId
     */
    void submitRepositoryIndex(Path repositoryPath, Long repositoryId);

    /**
     * 提交项目空间级索引任务（全量或重新索引）。
     *
     * @param repositoryPath     项目空间根路径
     * @param codegraphIndexPath CodeGraph 索引目录路径
     * @param projectSpaceId     项目空间 ID，作为任务 businessId
     * @param reindex            是否重新索引（先删除旧索引再全量重建）
     */
    void submitProjectSpaceIndex(Path repositoryPath, Path codegraphIndexPath,
                                  Long projectSpaceId, boolean reindex);

    /**
     * 提交项目空间级增量同步任务。
     *
     * @param repositoryPath 项目空间根路径
     * @param projectSpaceId 项目空间 ID，作为任务 businessId
     */
    void submitProjectSpaceSync(Path repositoryPath, Long projectSpaceId);

    /**
     * 开始跟踪项目空间的索引进度。
     *
     * <p>在索引前置校验通过、状态流转为 INDEXING 后调用，
     * 初始化进度跟踪器以便后续 {@link #getProgress(Long)} 查询。</p>
     *
     * @param projectSpaceId 项目空间 ID
     */
    void startProgress(Long projectSpaceId);

    /**
     * 查询项目空间的当前索引进度。
     *
     * @param projectSpaceId 项目空间 ID
     * @return 进度快照，永不返回 null（未开始时返回 0% 未开始）
     */
    IndexProgress getProgress(Long projectSpaceId);

    /**
     * 索引进度快照。
     */
    @lombok.Value
    class IndexProgress {
        /** 进度百分比（0-100），-1 表示保留上次百分比。 */
        int percent;
        /** 进度描述信息。 */
        String message;
        /** 是否已完成（成功或失败）。 */
        boolean completed;
    }
}
