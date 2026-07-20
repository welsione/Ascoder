package cn.welsione.ascoder.common;

/**
 * 进程中止信号，允许行回调在检测到致命错误时请求提前终止子进程。
 *
 * <p>典型场景：CodeGraph CLI 因 EAGAIN 等原因输出 {@code Uncaught exception} 后进程僵死，
 * pump 线程仍在阻塞等待输出，导致 {@code process.waitFor()} 一直等到超时（默认 3600s），
 * 项目空间状态卡在 {@code INDEXING}。通过此信号，回调可立即触发进程终止。</p>
 */
public class AbortSignal {

    private volatile boolean aborted;

    public void abort() {
        this.aborted = true;
    }

    public boolean isAborted() {
        return aborted;
    }
}
