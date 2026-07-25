package cn.welsione.ascoder.codegraph.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CodeGraph 增量同步异步任务上下文。
 *
 * <p>由 {@link CodeGraphSyncTaskDefinition} 序列化/反序列化。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeGraphSyncContext {
    /** 项目空间路径。 */
    String repositoryPath;
    /** 项目空间 ID。 */
    Long projectSpaceId;
}
