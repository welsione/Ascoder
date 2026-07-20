package cn.welsione.ascoder.common;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * 外部命令执行器接口，支持同步和异步执行模式。
 */
public interface CommandRunner {

    /**
     * 同步执行命令，等待命令完成后返回结果。
     */
    CommandResult run(List<String> command, Path workdir, Duration timeout);

    /**
     * 异步执行命令，实时输出每一行到回调。
     *
     * @param command  命令及参数
     * @param workdir  工作目录
     * @param timeout  超时时间
     * @param onLine   每行输出的回调
     * @return 最终执行结果
     */
    CommandResult runAsync(List<String> command, Path workdir, Duration timeout, Consumer<String> onLine);
}
