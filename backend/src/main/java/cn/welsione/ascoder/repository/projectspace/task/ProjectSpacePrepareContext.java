package cn.welsione.ascoder.repository.projectspace.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目空间准备异步任务上下文，仅携带项目空间 ID。
 *
 * <p>由 {@link ProjectSpacePrepareTaskDefinition} 序列化/反序列化。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSpacePrepareContext {
    /** 项目空间 ID。 */
    Long projectSpaceId;
}
