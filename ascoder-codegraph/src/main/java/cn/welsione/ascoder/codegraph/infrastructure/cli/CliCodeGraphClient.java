package cn.welsione.ascoder.codegraph.infrastructure.cli;

import cn.welsione.ascoder.codegraph.CodeGraphConfig;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.AbortSignal;
import cn.welsione.ascoder.common.CommandResult;
import cn.welsione.ascoder.common.SafePathValidator;
import cn.welsione.ascoder.common.TextUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CodeGraph CLI 客户端实现，通过调用 codegraph 命令行工具执行代码分析操作。
 */
@Slf4j
public class CliCodeGraphClient implements CodeGraphClient {

    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+)%");
    private static final int DEFAULT_MAX_FILES = 20;

    private final CodeGraphCommandRunner commandRunner;
    private final IndexProgressTracker indexProgressTracker;
    private final CodeGraphConfig config;

    public CliCodeGraphClient(
            CodeGraphConfig config,
            CodeGraphCommandRunner commandRunner,
            IndexProgressTracker indexProgressTracker
    ) {
        this.config = config;
        this.commandRunner = commandRunner;
        this.indexProgressTracker = indexProgressTracker;
    }

    @Override
    public CodeGraphToolResult index(Path repositoryPath) {
        return index(repositoryPath, null);
    }

    @Override
    public CodeGraphToolResult index(Path repositoryPath, Long projectSpaceId) {
        return index(repositoryPath, repositoryPath.resolve(".codegraph"), projectSpaceId);
    }

    @Override
    public CodeGraphToolResult index(Path repositoryPath, Path codegraphIndexPath, Long projectSpaceId) {
        log.info("执行 CodeGraph 索引，路径={}", repositoryPath);
        assertEffectiveIndexPath(repositoryPath, codegraphIndexPath);
        CommandResult init = commandRunner.run(
                List.of(config.getExecutable(), "init", repositoryPath.toString()),
                repositoryPath,
                config.getIndexTimeout()
        );

        if (!init.isSuccess()) {
            log.warn("CodeGraph init 失败，路径={}", repositoryPath);
            return new CodeGraphToolResult(false, init.getOutput());
        }

        StringBuilder output = new StringBuilder();
        AbortSignal abortSignal = new AbortSignal();
        CliIndexCallbacks callbacks = new CliIndexCallbacks(output, repositoryPath, projectSpaceId, abortSignal);
        CommandResult index = commandRunner.runAsync(
                List.of(config.getExecutable(), "index", repositoryPath.toString()),
                repositoryPath,
                config.getIndexTimeout(),
                callbacks,
                abortSignal
        );

        if (callbacks.isFatalError()) {
            log.error("CodeGraph 索引检测到致命错误，路径={}，错误={}", repositoryPath, callbacks.getFatalMessage());
            return new CodeGraphToolResult(false, callbacks.getFatalMessage());
        }

        log.info("CodeGraph 索引完成，路径={}，成功={}", repositoryPath, index.isSuccess());
        return new CodeGraphToolResult(index.isSuccess(), init.getOutput() + "\n" + output);
    }

    @Override
    public boolean hasIndex(Path repositoryPath) {
        return Files.isDirectory(repositoryPath.toAbsolutePath().normalize().resolve(".codegraph"));
    }

    @Override
    public CodeGraphToolResult sync(Path repositoryPath) {
        return sync(repositoryPath, null);
    }

    @Override
    public CodeGraphToolResult sync(Path repositoryPath, Long projectSpaceId) {
        log.info("执行 CodeGraph 增量同步，路径={}", repositoryPath);
        StringBuilder output = new StringBuilder();
        CommandResult result = commandRunner.runAsync(
                List.of(config.getExecutable(), "sync", repositoryPath.toString()),
                repositoryPath,
                config.getIndexTimeout(),
                line -> {
                    if (!output.isEmpty()) {
                        output.append("\n");
                    }
                    output.append(line);
                    if (projectSpaceId != null) {
                        indexProgressTracker.update(projectSpaceId, -1, TextUtil.stripAnsi(line.trim()));
                    }
                    log.debug("CodeGraph 同步输出 [{}]: {}", repositoryPath, line.trim());
                }
        );
        log.info("CodeGraph 增量同步完成，路径={}，成功={}", repositoryPath, result.isSuccess());
        return new CodeGraphToolResult(result.isSuccess(), output.toString());
    }

    private void assertEffectiveIndexPath(Path repositoryPath, Path codegraphIndexPath) {
        Path expected = repositoryPath.toAbsolutePath().normalize().resolve(".codegraph").normalize();
        Path actual = codegraphIndexPath.toAbsolutePath().normalize();
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("当前 CodeGraph CLI 仅支持仓库目录下的 .codegraph 索引，期望 "
                    + expected + "，实际 " + actual);
        }
    }

    private int extractPercent(String line) {
        Matcher matcher = PERCENT_PATTERN.matcher(line);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
        return -1;
    }

    private String stripAnsi(String text) {
        return TextUtil.stripAnsi(text);
    }

    /**
     * 索引命令的行回调，跟踪进度并检测 CLI 致命错误（如 EAGAIN 导致进程僵死）。
     * 检测到致命错误时触发 {@link AbortSignal} 请求主线程提前终止子进程。
     */
    private class CliIndexCallbacks implements java.util.function.Consumer<String> {

        private static final java.util.regex.Pattern FATAL_PATTERN = java.util.regex.Pattern.compile(
                "Uncaught exception|SIGSEGV|SIGABRT|FATAL ERROR"
        );

        private final StringBuilder output;
        private final Path repositoryPath;
        private final Long projectSpaceId;
        private final AbortSignal abortSignal;
        private volatile boolean fatalError;
        private volatile String fatalMessage;

        CliIndexCallbacks(StringBuilder output, Path repositoryPath, Long projectSpaceId, AbortSignal abortSignal) {
            this.output = output;
            this.repositoryPath = repositoryPath;
            this.projectSpaceId = projectSpaceId;
            this.abortSignal = abortSignal;
        }

        @Override
        public void accept(String line) {
            synchronized (output) {
                if (!output.isEmpty()) {
                    output.append("\n");
                }
                output.append(line);
            }
            int percent = extractPercent(line);
            String cleanLine = TextUtil.stripAnsi(line.trim());
            if (percent >= 0 && projectSpaceId != null) {
                indexProgressTracker.update(projectSpaceId, percent, cleanLine);
            }
            if (line.contains("%") || line.contains("Phase:")) {
                log.info("CodeGraph 索引进度 [{}]: {}", repositoryPath, line.trim());
            } else {
                log.debug("CodeGraph 索引输出 [{}]: {}", repositoryPath, line.trim());
            }

            if (!fatalError && FATAL_PATTERN.matcher(line).find()) {
                fatalError = true;
                fatalMessage = "CodeGraph CLI 致命错误: " + cleanLine;
                log.error("检测到 CodeGraph CLI 致命错误，路径={}，行={}", repositoryPath, cleanLine);
                abortSignal.abort();
            }
        }

        boolean isFatalError() {
            return fatalError;
        }

        String getFatalMessage() {
            return fatalMessage;
        }
    }

    @Override
    public CodeGraphToolResult context(Path repositoryPath, String question) {
        log.debug("查询 CodeGraph context，路径={}，问题={}", repositoryPath, question);
        SafePathValidator.sanitizeArg(question);
        List<String> cmd = command(config.getExecutable(), "explore", question, "--path", repositoryPath.toString());
        addNumberOption(cmd, "--max-files", DEFAULT_MAX_FILES);
        return runTool(cmd, repositoryPath);
    }

    @Override
    public CodeGraphToolResult query(Path repositoryPath, String search, Integer limit, String kind) {
        SafePathValidator.sanitizeArg(search);
        List<String> command = command(config.getExecutable(), "query", search, "--path", repositoryPath.toString());
        addNumberOption(command, "--limit", limit);
        addTextOption(command, "--kind", kind);
        return runTool(command, repositoryPath);
    }

    @Override
    public CodeGraphToolResult files(Path repositoryPath, String filter, String pattern, String format, Integer maxDepth) {
        List<String> command = command(config.getExecutable(), "files", "--path", repositoryPath.toString());
        addTextOption(command, "--filter", filter);
        addTextOption(command, "--pattern", pattern);
        addTextOption(command, "--format", format);
        addNumberOption(command, "--max-depth", maxDepth);
        return runTool(command, repositoryPath);
    }

    @Override
    public CodeGraphToolResult callers(Path repositoryPath, String symbol, Integer limit) {
        SafePathValidator.sanitizeArg(symbol);
        List<String> command = command(config.getExecutable(), "callers", symbol, "--path", repositoryPath.toString());
        addNumberOption(command, "--limit", limit);
        return runTool(command, repositoryPath);
    }

    @Override
    public CodeGraphToolResult callees(Path repositoryPath, String symbol, Integer limit) {
        SafePathValidator.sanitizeArg(symbol);
        List<String> command = command(config.getExecutable(), "callees", symbol, "--path", repositoryPath.toString());
        addNumberOption(command, "--limit", limit);
        return runTool(command, repositoryPath);
    }

    @Override
    public CodeGraphToolResult impact(Path repositoryPath, String symbol, Integer depth) {
        SafePathValidator.sanitizeArg(symbol);
        List<String> command = command(config.getExecutable(), "impact", symbol, "--path", repositoryPath.toString());
        addNumberOption(command, "--depth", depth);
        return runTool(command, repositoryPath);
    }

    @Override
    public CodeGraphToolResult affected(Path repositoryPath, String files, Integer depth, String filter) {
        List<String> command = command(config.getExecutable(), "affected", "--path", repositoryPath.toString());
        if (files != null && !files.isBlank()) {
            Arrays.stream(files.split("[\\n,]"))
                    .map(String::trim)
                    .filter(file -> !file.isBlank())
                    .map(file -> SafePathValidator.validateUnderRoot(repositoryPath, file).toString())
                    .forEach(command::add);
        }
        addNumberOption(command, "--depth", depth);
        addTextOption(command, "--filter", filter);
        return runTool(command, repositoryPath);
    }

    @Override
    public CodeGraphToolResult explore(Path repositoryPath, String query, Integer maxFiles) {
        log.debug("查询 CodeGraph explore，路径={}，查询={}", repositoryPath, query);
        SafePathValidator.sanitizeArg(query);
        List<String> cmd = command(config.getExecutable(), "explore", query, "--path", repositoryPath.toString());
        addNumberOption(cmd, "--max-files", maxFiles);
        return runTool(cmd, repositoryPath);
    }

    @Override
    public CodeGraphToolResult node(Path repositoryPath, String name, String file, Integer offset, Integer limit) {
        log.debug("查询 CodeGraph node，路径={}，符号={}", repositoryPath, name);
        SafePathValidator.sanitizeArg(name);
        List<String> cmd = command(config.getExecutable(), "node", name, "--path", repositoryPath.toString());
        addTextOption(cmd, "--file", file);
        addNumberOption(cmd, "--offset", offset);
        addNumberOption(cmd, "--limit", limit);
        return runTool(cmd, repositoryPath);
    }

    private CodeGraphToolResult runTool(List<String> command, Path repositoryPath) {
        log.debug("执行 CodeGraph 命令：{}", String.join(" ", command));
        CommandResult result = commandRunner.run(command, repositoryPath, config.getTimeout());
        return new CodeGraphToolResult(result.isSuccess(), result.getOutput());
    }

    private List<String> command(String... parts) {
        return new ArrayList<>(Arrays.asList(parts));
    }

    private void addTextOption(List<String> command, String name, String value) {
        if (value != null && !value.isBlank()) {
            String trimmed = value.trim();
            SafePathValidator.sanitizeArg(trimmed);
            command.add(name);
            command.add(trimmed);
        }
    }

    private void addNumberOption(List<String> command, String name, Integer value) {
        if (value != null && value > 0) {
            command.add(name);
            command.add(value.toString());
        }
    }
}
