package cn.welsione.ascoder.common;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 异步命令执行器抽象类，支持实时读取命令输出并逐行回调。
 *
 * <p>使用 daemon drain 线程阻塞读取输出（BUG-3：旧实现用 {@code reader.ready()}
 * + {@code Thread.sleep(100)} 忙等，CPU 占用高且可能漏掉子进程退出后残留的输出）。</p>
 *
 * <p>支持回调请求提前中止：当行回调设置 {@link AbortSignal} 时，主线程会在检测到中止信号后
 * 立即终止子进程并返回失败结果，避免进程僵死后长时间卡在 {@code INDEXING} 状态。</p>
 */
@Slf4j
public abstract class AsyncCommandRunner implements CommandRunner {

    private static final int PUMP_JOIN_TIMEOUT_MS = 500;

    @Override
    public CommandResult run(List<String> command, Path workdir, Duration timeout) {
        StringBuilder output = new StringBuilder();
        return runAsync(command, workdir, timeout, line -> {
            if (!output.isEmpty()) {
                output.append('\n');
            }
            output.append(line);
        });
    }

    @Override
    public CommandResult runAsync(List<String> command, Path workdir, Duration timeout, Consumer<String> onLine) {
        return runAsync(command, workdir, timeout, onLine, new AbortSignal());
    }

    /**
     * 带中止信号的异步执行。行回调可通过 {@link AbortSignal#abort()} 请求提前终止子进程。
     */
    public CommandResult runAsync(List<String> command, Path workdir, Duration timeout,
                                  Consumer<String> onLine, AbortSignal abortSignal) {
        log.debug("异步执行命令：{}，工作目录={}", String.join(" ", command), workdir);
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true);

        getEnvironment().forEach(processBuilder.environment()::put);

        Process process = null;
        StringBuilder output = new StringBuilder();
        try {
            process = processBuilder.start();
            Thread pump = startPump(process, output, onLine, abortSignal);

            long timeoutMs = timeout == null ? 30_000 : Math.max(1, timeout.toMillis());
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (System.currentTimeMillis() < deadline && process.isAlive() && !abortSignal.isAborted()) {
                process.waitFor(Math.min(1000, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            }

            if (abortSignal.isAborted() && process.isAlive()) {
                process.destroyForcibly();
                pump.join(PUMP_JOIN_TIMEOUT_MS);
                log.warn("命令因回调请求中止：{}", String.join(" ", command));
                return new CommandResult(false, output + "\n命令因致命错误中止");
            }

            boolean finished = !process.isAlive();
            pump.join(PUMP_JOIN_TIMEOUT_MS);

            if (!finished) {
                process.destroyForcibly();
                log.warn("命令执行超时：{}，超时时间={}s", String.join(" ", command), timeout.toSeconds());
                return new CommandResult(false, output + "\n命令执行超时");
            }
            log.debug("命令执行完成，退出码={}", process.exitValue());
            return new CommandResult(process.exitValue() == 0, output.toString());
        } catch (IOException ex) {
            log.error("无法执行命令：{}", ex.getMessage(), ex);
            return new CommandResult(false, "无法执行命令：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CommandResult(false, output + "\n命令被中断");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Thread startPump(Process process, StringBuilder sink, Consumer<String> onLine, AbortSignal abortSignal) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (sink) {
                        if (sink.length() > 0) {
                            sink.append('\n');
                        }
                        sink.append(line);
                    }
                    if (onLine != null) {
                        try {
                            onLine.accept(line);
                        } catch (Exception ex) {
                            log.debug("行回调异常：{}", ex.getMessage());
                        }
                    }
                    if (abortSignal.isAborted()) {
                        break;
                    }
                }
            } catch (IOException ex) {
                log.debug("pump 流关闭：{}", ex.getMessage());
            }
        }, "async-cmd-pump");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 获取环境变量映射，子类可覆盖以添加自定义环境变量。
     */
    protected abstract java.util.Map<String, String> getEnvironment();
}
