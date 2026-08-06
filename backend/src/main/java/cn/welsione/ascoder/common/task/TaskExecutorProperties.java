package cn.welsione.ascoder.common.task;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步任务线程池默认参数，对应 application.yml 中 {@code ascoder.task-executor.*} 节点。
 *
 * <p>运行时实际值由 {@code RuntimeSettingsService} 读取（设置页可修改，重启生效）；
 * 本类仅作为白名单写入门控与默认值兜底。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ascoder.task-executor")
public class TaskExecutorProperties {

    private int gitCloneCoreThreads = 1;
    private int gitCloneMaxThreads = 2;
    private int gitCloneQueueCapacity = 4;

    private int gitFetchCoreThreads = 2;
    private int gitFetchMaxThreads = 4;
    private int gitFetchQueueCapacity = 8;

    private int codegraphIndexCoreThreads = 1;
    private int codegraphIndexMaxThreads = 1;
    private int codegraphIndexQueueCapacity = 2;

    private int codegraphSyncCoreThreads = 1;
    private int codegraphSyncMaxThreads = 2;
    private int codegraphSyncQueueCapacity = 4;

    private int projectSpacePrepareCoreThreads = 1;
    private int projectSpacePrepareMaxThreads = 2;
    private int projectSpacePrepareQueueCapacity = 4;

    private int branchRefreshCoreThreads = 1;
    private int branchRefreshMaxThreads = 2;
    private int branchRefreshQueueCapacity = 8;
}
