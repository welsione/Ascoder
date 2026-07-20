package cn.welsione.ascoder.repository;

/**
 * 仓库分支引用来源，用于区分本地 heads、远端 heads 和本地远端跟踪引用。
 */
public enum RepositoryBranchSourceKind {
    LOCAL_HEAD,
    REMOTE_HEAD,
    REMOTE_TRACKING
}
