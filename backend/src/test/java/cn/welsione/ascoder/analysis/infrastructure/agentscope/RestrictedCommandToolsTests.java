package cn.welsione.ascoder.analysis.infrastructure.agentscope;

import cn.welsione.ascoder.analysis.CodeGraphWorkspaceContext;
import cn.welsione.ascoder.analysis.RestrictedCommandTools;
import cn.welsione.ascoder.common.SafeCommandRunner;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestrictedCommandToolsTests {

    private Path repoRoot;
    private RestrictedCommandTools tools;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        this.repoRoot = tempDir;
        Files.writeString(tempDir.resolve("hello.txt"), "Hello World\nLine 2\n");
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/file.txt"), "sub content");

        CodeGraphWorkspaceContext ctx = CodeGraphWorkspaceContext.single(repoRoot);
        tools = new RestrictedCommandTools(
                new SafeCommandRunner(), ctx, new AtomicReference<>(""), "ProjectSpace: test"
        );
    }

    @Test
    void registerAllRegistersTwoToolMethods() {
        Toolkit toolkit = new Toolkit();
        tools.registerAll(toolkit);

        assertThat(toolkit.getToolNames())
                .containsExactlyInAnyOrder("run_safe_command", "run_safe_pipe");
    }

    @Test
    void runCommandCatHello() {
        ToolResultBlock result = tools.runCommand("cat", List.of("hello.txt"), null, null).block();
        String text = extractText(result);
        assertThat(text).contains("Hello World");
    }

    @Test
    void runCommandRejectsCommandNotInAllowlist() {
        ToolResultBlock result = tools.runCommand("rm", List.of("-rf", "/"), null, null).block();
        assertThat(extractText(result)).contains("not in allowlist");
    }

    @Test
    void runCommandRejectsShellMetacharactersInArg() {
        ToolResultBlock result = tools.runCommand("cat", List.of("hello; rm -rf /"), null, null).block();
        assertThat(extractText(result)).contains("forbidden characters");
    }

    /**
     * 注入测试：head 的行数参数位置传入 `; rm -rf /` 等恶意值，必须被拒绝，
     * 不会作为 shell 命令执行。验证所有参数（含选项类参数）均经 sanitizeArg。
     */
    @Test
    void runCommandRejectsInjectionInOptionArg() {
        ToolResultBlock result = tools.runCommand("head", List.of("-1; rm -rf /", "hello.txt"), null, null).block();
        assertThat(extractText(result)).contains("forbidden characters");
        // 确认目标文件未被删除（命令未执行）
        assertThat(Files.exists(repoRoot.resolve("hello.txt"))).isTrue();
    }

    /**
     * 注入测试：参数含管道符 `|` 等元字符必须被拒绝，不会触发管道注入。
     */
    @Test
    void runCommandRejectsPipeInjectionInArg() {
        ToolResultBlock result = tools.runCommand("cat", List.of("hello.txt | wc -l"), null, null).block();
        assertThat(extractText(result)).contains("forbidden characters");
    }

    @Test
    void runCommandRejectsPathTraversal() {
        ToolResultBlock result = tools.runCommand("cat", List.of("../etc/passwd"), null, null).block();
        assertThat(extractText(result)).containsAnyOf("out of bounds", "..");
    }

    @Test
    void runCommandReadsFileInWorkdir() {
        ToolResultBlock result = tools.runCommand("cat", List.of("hello.txt"), null, null).block();
        String text = extractText(result);
        assertThat(text)
                .contains("Hello World")
                .contains("Line 2");
    }

    @Test
    void runCommandAllowsHeadAndTail() {
        ToolResultBlock head = tools.runCommand("head", List.of("-1", "hello.txt"), null, null).block();
        ToolResultBlock tail = tools.runCommand("tail", List.of("-1", "hello.txt"), null, null).block();
        assertThat(extractText(head)).contains("Hello World");
        assertThat(extractText(tail)).contains("Line 2");
    }

    @Test
    void runCommandRejectsSleepNotInAllowlist() {
        ToolResultBlock result = tools.runCommand("sleep", List.of("1"), null, null).block();
        assertThat(extractText(result)).contains("not in allowlist");
    }

    @Test
    void runCommandRejectsEmptyCommand() {
        ToolResultBlock result = tools.runCommand("", null, null, null).block();
        assertThat(extractText(result)).contains("not in allowlist");
    }

    @Test
    void runCommandDuplicateIsIntercepted() {
        tools.runCommand("cat", List.of("hello.txt"), null, null).block();
        ToolResultBlock result = tools.runCommand("cat", List.of("hello.txt"), null, null).block();
        assertThat(extractText(result)).contains("Duplicate call intercepted");
    }

    @Test
    void runPipeExecutesTwoStagePipeline() {
        ToolResultBlock result = tools.runPipe(
                List.of(
                        new RestrictedCommandTools.PipeStage("cat", List.of("hello.txt")),
                        new RestrictedCommandTools.PipeStage("grep", List.of("World"))
                ),
                null, null
        ).block();

        String text = extractText(result);
        assertThat(text).contains("Hello World");
    }

    @Test
    void runPipeRejectsMoreThanFiveStages() {
        List<RestrictedCommandTools.PipeStage> stages = List.of(
                new RestrictedCommandTools.PipeStage("cat", List.of("hello.txt")),
                new RestrictedCommandTools.PipeStage("grep", List.of("World")),
                new RestrictedCommandTools.PipeStage("wc", List.of("-l")),
                new RestrictedCommandTools.PipeStage("head", List.of("-1")),
                new RestrictedCommandTools.PipeStage("tail", List.of("-1")),
                new RestrictedCommandTools.PipeStage("cat", List.of())
        );
        ToolResultBlock result = tools.runPipe(stages, null, null).block();
        assertThat(extractText(result)).contains("at most");
    }

    @Test
    void runPipeRejectsCommandNotInAllowlist() {
        ToolResultBlock result = tools.runPipe(
                List.of(
                        new RestrictedCommandTools.PipeStage("cat", List.of("hello.txt")),
                        new RestrictedCommandTools.PipeStage("rm", List.of("hello.txt"))
                ),
                null, null
        ).block();
        assertThat(extractText(result)).contains("allowlist");
    }

    /**
     * 注入测试：pipe stage 的 args 含 shell 元字符必须被拒绝，不会执行注入命令。
     */
    @Test
    void runPipeRejectsShellMetacharactersInStageArg() {
        ToolResultBlock result = tools.runPipe(
                List.of(
                        new RestrictedCommandTools.PipeStage("cat", List.of("hello.txt")),
                        new RestrictedCommandTools.PipeStage("grep", List.of("World; rm -rf /"))
                ),
                null, null
        ).block();
        assertThat(extractText(result)).contains("forbidden characters");
        assertThat(Files.exists(repoRoot.resolve("hello.txt"))).isTrue();
    }

    private String extractText(ToolResultBlock block) {
        if (block == null || block.getOutput() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ContentBlock output : block.getOutput()) {
            if (output instanceof TextBlock textBlock && textBlock.getText() != null) {
                builder.append(textBlock.getText());
            }
        }
        return builder.toString();
    }
}
