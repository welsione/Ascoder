package cn.welsione.ascoder.repository.projectspace;

import lombok.Value;

/**
 * 项目空间关联仓库 fetch 完成事件。
 *
 * <p>当项目空间触发拉取操作后，各成员仓库的异步 fetch 任务完成时发布此事件，
 * 由 {@link ProjectSpaceService} 监听并刷新项目空间状态，确保提交记录等数据及时更新。</p>
 */
@Value
public class ProjectSpaceFetchCompletedEvent {
    Long projectSpaceId;
}
