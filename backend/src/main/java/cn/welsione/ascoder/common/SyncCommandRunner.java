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
 * 同步命令执行器抽象类，提供同步执行命令的基础实现。
 *
 * <p>使用独立的 drain 线程读取子进程输出，避免 pipe buffer 满导致子进程阻塞死锁
 * （BUG-3：旧实现先 {@code waitFor} 再 {@code readAllBytes} 在大输出时会死锁）。</p>
 */
@Slf4j
public abstract class SyncCommandRunner implements CommandRunner {

    private static final int PUMP_JOIN_TIMEOUT_MS = 500;

    @Override
    public CommandResult run(List<String> command, Path workdir, Duration timeout) {
        log.debug("执行命令：{}，工作目录={}", String.join(" ", command), workdir);
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true);

        getEnvironment().forEach(processBuilder.environment()::put);

        Process process = null;
        StringBuilder output = new StringBuilder();
        try {
            process = processBuilder.start();
            Thread pump = startPump(process, output, null);

            long timeoutMs = timeout == null ? 30_000 : Math.max(1, timeout.toMillis());
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
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
            log.warn("命令被中断");
            return new CommandResult(false, "命令被中断");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    @Override
    public CommandResult runAsync(List<String> command, Path workdir, Duration timeout, Consumer<String> onLine) {
        // 同步执行器以同步模式实现：在内部启动 drain 线程，逐行回调。
        log.debug("同步执行器以行回调模式运行：{}", String.join(" ", command));
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true);

        getEnvironment().forEach(processBuilder.environment()::put);

        Process process = null;
        StringBuilder output = new StringBuilder();
        try {
            process = processBuilder.start();
            Thread pump = startPump(process, output, onLine);

            long timeoutMs = timeout == null ? 30_000 : Math.max(1, timeout.toMillis());
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            pump.join(PUMP_JOIN_TIMEOUT_MS);

            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, output + "\n命令执行超时");
            }
            return new CommandResult(process.exitValue() == 0, output.toString());
        } catch (IOException ex) {
            return new CommandResult(false, "无法执行命令：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CommandResult(false, "命令被中断");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 启动 daemon drain 线程读取子进程合并后的输出（已 redirectErrorStream）。
     */
    private Thread startPump(Process process, StringBuilder sink, Consumer<String> onLine) {
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
                }
            } catch (IOException ex) {
                log.debug("pump 流关闭：{}", ex.getMessage());
            }
        }, "sync-cmd-pump");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 获取环境变量映射，子类可覆盖以添加自定义环境变量。
     */
    protected abstract java.util.Map<String, String> getEnvironment();
}
