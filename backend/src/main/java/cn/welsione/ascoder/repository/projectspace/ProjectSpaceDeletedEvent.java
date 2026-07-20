package cn.welsione.ascoder.repository.projectspace;

import lombok.Value;

/**
 * 项目空间删除事件。
 *
 * <p>用于通知其他聚合（Question、Conversation 等）解除对此项目空间的引用，
 * 避免在 {@link ProjectSpaceService} 内直接跨聚合执行 JPQL。</p>
 */
@Value
public class ProjectSpaceDeletedEvent {
    Long projectSpaceId;
}
