package cn.welsione.ascoder.common;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 受限命令执行器，专为 LLM 工具调用设计。特性：
 * <ul>
 *   <li>不依赖 {@link AsyncCommandRunner} 抽象类，组合方式实现</li>
 *   <li>stdout/stderr 各起一个 drain 线程，避免 pipe buffer 阻塞</li>
 *   <li>强超时 + destroyForcibly 兜底</li>
 *   <li>输出按 8000 字符截断（与 CodeGraphToolSupport 对齐）</li>
 *   <li>不依赖 reader.ready()，避免忙等 CPU 100%</li>
 * </ul>
 */
@Slf4j
public class SafeCommandRunner {

    private static final int MAX_OUTPUT_LENGTH = 8000;
    private static final int PUMP_JOIN_TIMEOUT_MS = 500;

    /**
     * 同步执行命令，超时强杀。
     *
     * @param command    命令 + 参数（第一个是程序名）
     * @param workingDir 工作目录
     * @param timeout    超时
     * @param env        额外环境变量（可为 null）
     * @return 命令结果
     */
    public CommandResult run(List<String> command, Path workingDir, Duration timeout, Map<String, String> env) {
        if (command == null || command.isEmpty()) {
            return new CommandResult(false, "命令不能为空");
        }
        Process process = null;
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(command).directory(workingDir.toFile()).redirectErrorStream(false);
            if (env != null) {
                pb.environment().putAll(env);
            }
            process = pb.start();
            long timeoutMs = timeout == null ? 30_000 : Math.max(1, timeout.toMillis());

            // 启动 stdout/stderr drain 线程
            Thread stdoutPump = startPump(process.getInputStream(), output, false);
            Thread stderrPump = startPump(process.getErrorStream(), output, true);

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            stdoutPump.join(PUMP_JOIN_TIMEOUT_MS);
            stderrPump.join(PUMP_JOIN_TIMEOUT_MS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("命令执行超时：command={}，timeoutMs={}", String.join(" ", command), timeoutMs);
                return new CommandResult(false, output + "\n命令执行超时");
            }
            int exitCode = process.exitValue();
            return new CommandResult(exitCode == 0, output.toString());
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
     * 启动一个守护线程把流排空到 StringBuilder，避免 pipe buffer 满导致子进程阻塞。
     * 标记为 daemon，JVM 退出时不会阻止。
     */
    private Thread startPump(InputStream stream, StringBuilder sink, boolean isStderr) {
        AtomicLong totalRead = new AtomicLong(0);
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (totalRead.addAndGet(line.length() + 1) > MAX_OUTPUT_LENGTH) {
                        // 已超过截断阈值：追加截断标记后停止读取
                        synchronized (sink) {
                            if (sink.length() < MAX_OUTPUT_LENGTH) {
                                sink.append("\n... (输出已截断，共 ")
                                        .append(totalRead.get())
                                        .append(" 字符)");
                            }
                        }
                        // 继续 drain 防止子进程阻塞，但不写入 sink
                        continue;
                    }
                    synchronized (sink) {
                        if (sink.length() > 0) {
                            sink.append('\n');
                        }
                        sink.append(isStderr ? "[err] " : "").append(line);
                    }
                }
            } catch (IOException ex) {
                log.debug("pump 流关闭：{}", ex.getMessage());
            }
        }, isStderr ? "cmd-stderr-pump" : "cmd-stdout-pump");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
