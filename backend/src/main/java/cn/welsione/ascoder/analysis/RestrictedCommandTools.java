package cn.welsione.ascoder.analysis;

import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.CommandResult;
import cn.welsione.ascoder.common.SafeCommandRunner;
import cn.welsione.ascoder.common.SafePathValidator;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 受限 shell 命令工具集，提供 2 个 @Tool：
 * <ul>
 *   <li>{@code run_safe_command}：执行白名单只读命令</li>
 *   <li>{@code run_safe_pipe}：执行白名单命令管道（最多 5 个 stage）</li>
 * </ul>
 * <p>
 * 安全机制：
 * <ul>
 *   <li>命令名严格白名单（16 个只读工具）</li>
 *   <li>所有参数通过 {@link SafePathValidator#sanitizeArg} 拒绝 shell 元字符</li>
 *   <li>路径类参数验证必须落在工作目录内</li>
 *   <li>5 秒强超时 + destroyForcibly 兜底</li>
 * </ul>
 */
@Slf4j
public class RestrictedCommandTools {

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "cat", "head", "tail", "ls", "wc", "file", "stat", "du",
            "tree", "find", "grep", "awk", "cut", "sort", "uniq", "tr"
    );

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_PIPE_STAGES = 5;
    private static final int MIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_TIMEOUT_SECONDS = 30;
    private static final int MAX_PIPE_OUTPUT_LENGTH = 8000;

    private final SafeCommandRunner commandRunner;
    private final CodeGraphWorkspaceContext workspaceContext;
    private final CodeGraphToolSupport support;

    public RestrictedCommandTools(SafeCommandRunner commandRunner,
                                  CodeGraphWorkspaceContext workspaceContext,
                                  AtomicReference<String> codeContext,
                                  String metadata) {
        this.commandRunner = commandRunner;
        this.workspaceContext = workspaceContext;
        this.support = new CodeGraphToolSupport(codeContext, metadata);
    }

    public void registerAll(Toolkit toolkit) {
        toolkit.registration().tool(this).apply();
    }

    // ===== @Tool 1: run_safe_command =====

    @Tool(
            name = "run_safe_command",
            description = """
                    Execute a read-only shell command. Allowed commands:
                    cat, head, tail, ls, wc, file, stat, du, tree, find,
                    grep, awk, cut, sort, uniq, tr. Working directory is
                    the repository root. Shell metacharacters are rejected.
                    """
    )
    public Mono<ToolResultBlock> runCommand(
            @ToolParam(name = "command", required = true, description = "Command name (must be in the allowlist).") String command,
            @ToolParam(name = "args", required = false, description = "Command arguments. Each is sanitized. Paths must be relative to repository root.") List<String> args,
            @ToolParam(name = "timeoutSeconds", required = false, description = "Timeout in seconds. Default is 5, max is 30.") Integer timeoutSeconds,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            if (!ALLOWED_COMMANDS.contains(command)) {
                return ToolResultBlock.error("Command not in allowlist: " + command
                        + ". Allowed: " + String.join(", ", ALLOWED_COMMANDS));
            }
            List<String> safeArgs = args == null ? List.of() : args;
            Path workingDir = workspaceContext.resolveGitPath(repositoryName);
            try {
                sanitizeArgs(safeArgs, workingDir);
            } catch (IllegalArgumentException ex) {
                return ToolResultBlock.error(ex.getMessage());
            }
            Duration timeout = clampTimeout(timeoutSeconds);

            List<String> fullCmd = new ArrayList<>();
            fullCmd.add(command);
            fullCmd.addAll(safeArgs);
            ToolResultBlock dup = support.checkDuplicate("run_safe_command",
                    command + " " + String.join(" ", safeArgs));
            String dedupeKey = command + " " + String.join(" ", safeArgs);
            if (dup != null) {
                return dup;
            }
            log.info("执行安全命令：command={}, args={}, timeout={}", command, safeArgs, timeout);
            CommandResult result = commandRunner.run(fullCmd, workingDir, timeout, Map.of());
            return support.toToolResult("run_safe_command", dedupeKey,
                    new CodeGraphToolResult(result.isSuccess(), result.getOutput()));
        });
    }

    // ===== @Tool 2: run_safe_pipe =====

    @Tool(
            name = "run_safe_pipe",
            description = """
                    Execute a pipeline of 2-5 safe commands connected by pipes.
                    Same allowlist and sanitization as run_safe_command.
                    Each stage must be in the allowlist.
                    """
    )
    public Mono<ToolResultBlock> runPipe(
            @ToolParam(name = "stages", required = true, description = "List of pipe stages. Each stage has command and args.") List<PipeStage> stages,
            @ToolParam(name = "timeoutSeconds", required = false, description = "Timeout in seconds. Default is 5, max is 30.") Integer timeoutSeconds,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name. Defaults to primary repository.") String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            if (stages == null || stages.isEmpty()) {
                return ToolResultBlock.error("run_safe_pipe requires at least 1 stage");
            }
            if (stages.size() > MAX_PIPE_STAGES) {
                return ToolResultBlock.error("run_safe_pipe allows at most " + MAX_PIPE_STAGES + " stages");
            }
            for (PipeStage stage : stages) {
                if (stage == null || stage.getCommand() == null || !ALLOWED_COMMANDS.contains(stage.getCommand())) {
                    return ToolResultBlock.error("Each stage must be in the allowlist: " + (stage == null ? "null" : stage.getCommand()));
                }
                List<String> safeArgs = stage.getArgs() == null ? List.of() : stage.getArgs();
                try {
                    sanitizeArgs(safeArgs, workspaceContext.resolveGitPath(repositoryName));
                } catch (IllegalArgumentException ex) {
                    return ToolResultBlock.error("stage '" + stage.getCommand() + "': " + ex.getMessage());
                }
            }
            Duration timeout = clampTimeout(timeoutSeconds);
            Path workingDir = workspaceContext.resolveGitPath(repositoryName);

            return runPipeInternal(stages, timeout, workingDir);
        });
    }

    /**
     * 使用并行 drain 线程驱动管道传输，避免 {@code transferTo} 同步阻塞导致
     * 中间进程 stdout 缓冲区满后死锁（BUG-9）。
     */
    private ToolResultBlock runPipeInternal(List<PipeStage> stages, Duration timeout, Path workingDir) {
        List<Process> processes = new ArrayList<>();
        List<Thread> pumps = new ArrayList<>();
        StringBuilder output = new StringBuilder("");

        try {
            for (int i = 0; i < stages.size(); i++) {
                PipeStage stage = stages.get(i);
                ProcessBuilder pb = new ProcessBuilder();
                pb.command().add(stage.getCommand());
                pb.command().addAll(stage.getArgs() == null ? List.of() : stage.getArgs());
                pb.directory(workingDir.toFile());
                // 合并 stderr→stdout 简化处理；前 n-1 个 stdout 被 pipe 消费不影响
                pb.redirectErrorStream(true);
                Process process = pb.start();
                processes.add(process);

                if (i > 0) {
                    // 启动 daemon 线程：将前一个进程的 stdout 泵入当前进程的 stdin
                    Process prev = processes.get(i - 1);
                    Thread pump = new Thread(() -> {
                        try (var prevOut = prev.getInputStream(); var curIn = process.getOutputStream()) {
                            prevOut.transferTo(curIn);
                        } catch (IOException ex) {
                            log.debug("pipe drain 结束：{}", ex.getMessage());
                        }
                    }, "pipe-pump-" + i);
                    pump.setDaemon(true);
                    pump.start();
                    pumps.add(pump);
                }
            }

            // 最后一个进程的 stdout：主线程读取
            Process last = processes.get(stages.size() - 1);
            try (var lastOut = last.getInputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = lastOut.read(buffer)) != -1) {
                    if (output.length() + n > MAX_PIPE_OUTPUT_LENGTH) {
                        output.append(new String(buffer, 0, Math.min(n, MAX_PIPE_OUTPUT_LENGTH - output.length())));
                        output.append("\n... (output truncated)");
                        // 继续读取但不追加，防止管道阻塞
                        continue;
                    }
                    output.append(new String(buffer, 0, n));
                }
            }

            boolean finished = last.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            // 等 drain 线程收尾
            for (Thread pump : pumps) {
                try { pump.join(500); } catch (InterruptedException ignored) { }
            }

            if (!finished) {
                processes.forEach(Process::destroyForcibly);
                return ToolResultBlock.error("Pipeline timed out");
            }
            if (last.exitValue() != 0) {
                processes.forEach(Process::destroyForcibly);
                return ToolResultBlock.error("Last stage exited with non-zero code: " + last.exitValue());
            }
            return new ToolResultBlock(null, null,
                    java.util.List.of(io.agentscope.core.message.TextBlock.builder()
                            .text(output.toString()).build()),
                    null);
        } catch (Exception ex) {
            processes.forEach(Process::destroyForcibly);
            return ToolResultBlock.error("Pipeline execution failed: " + ex.getMessage());
        } finally {
            processes.forEach(p -> {
                if (p.isAlive()) p.destroyForcibly();
            });
        }
    }

    // ===== 私有辅助 =====

    private void sanitizeArgs(List<String> args, Path workingDir) {
        for (String arg : args) {
            SafePathValidator.sanitizeArg(arg);
            if (looksLikePath(arg)) {
                try {
                    SafePathValidator.validateUnderRoot(workingDir, arg);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Path argument out of bounds: " + arg);
                }
            }
        }
    }

    private boolean looksLikePath(String arg) {
        if (arg == null) {
            return false;
        }
        if (arg.startsWith("-")) {
            return false;  // 命令行选项
        }
        return arg.contains("/") || arg.contains(".");
    }

    private Duration clampTimeout(Integer seconds) {
        int s = seconds == null || seconds <= 0 ? MIN_TIMEOUT_SECONDS : Math.min(seconds, MAX_TIMEOUT_SECONDS);
        return Duration.ofSeconds(s);
    }

    // ===== 辅助数据类 =====

    @Value
    @AllArgsConstructor
    public static class PipeStage {
        String command;
        List<String> args;
    }
}
