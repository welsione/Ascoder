package cn.welsione.ascoder.repository;

/**
 * 仓库删除事件，用于通知其他聚合解除对该仓库的引用。
 *
 * <p>questions / conversations 等历史记录通过 {@code repositoryId} nullable 外键引用仓库，
 * 删除仓库时由各自聚合的监听器将引用置 null，保留历史问答数据。</p>
 */
public class RepositoryDeletedEvent {

    private final Long repositoryId;

    public RepositoryDeletedEvent(Long repositoryId) {
        this.repositoryId = repositoryId;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }
}
