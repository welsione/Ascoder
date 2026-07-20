package cn.welsione.ascoder.codegraph.infrastructure.cli;

import cn.welsione.ascoder.common.AbortSignal;
import cn.welsione.ascoder.common.AsyncCommandRunner;
import cn.welsione.ascoder.common.CommandResult;
import cn.welsione.ascoder.common.CommandRunner;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * CodeGraph 命令执行器，通过组合 AsyncCommandRunner 实现命令执行，支持实时输出回调。
 *
 * <p>环境变量通过构造函数注入，不再依赖 Spring 的模板方法模式。</p>
 */
@Slf4j
public class CodeGraphCommandRunner implements CommandRunner {

    private final AsyncCommandRunner delegate;

    public CodeGraphCommandRunner(Map<String, String> environment) {
        this.delegate = new AsyncCommandRunner() {
            @Override
            protected Map<String, String> getEnvironment() {
                return environment;
            }
        };
    }

    @Override
    public CommandResult run(List<String> command, Path workdir, Duration timeout) {
        return delegate.run(command, workdir, timeout);
    }

    @Override
    public CommandResult runAsync(List<String> command, Path workdir, Duration timeout, Consumer<String> onLine) {
        return delegate.runAsync(command, workdir, timeout, onLine);
    }

    /**
     * 带中止信号的异步执行。
     */
    public CommandResult runAsync(List<String> command, Path workdir, Duration timeout,
                                  Consumer<String> onLine, AbortSignal abortSignal) {
        return delegate.runAsync(command, workdir, timeout, onLine, abortSignal);
    }
}
