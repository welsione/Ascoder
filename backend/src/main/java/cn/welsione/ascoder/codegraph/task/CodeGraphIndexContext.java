package cn.welsione.ascoder.codegraph.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CodeGraph 全量索引异步任务上下文。
 *
 * <p>由 {@link CodeGraphIndexTaskDefinition} 序列化/反序列化。支持两种索引场景：
 * {@link #projectSpaceId} 非空时为项目空间级索引，{@link #repositoryId} 非空时为仓库级索引。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeGraphIndexContext {
    /** 仓库或项目空间路径。 */
    private String repositoryPath;
    /** CodeGraph 索引目录路径（可选，项目空间级索引使用）。 */
    private String codegraphIndexPath;
    /** 是否重新索引（先删除旧索引再全量重建）。 */
    private boolean reindex;
    /** 项目空间 ID（可选，非空时执行项目空间级索引）。 */
    private Long projectSpaceId;
    /** 仓库 ID（可选，非空时执行仓库级索引）。 */
    private Long repositoryId;
}
