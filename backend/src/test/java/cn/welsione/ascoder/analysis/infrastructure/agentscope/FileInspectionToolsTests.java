package cn.welsione.ascoder.analysis.infrastructure.agentscope;

import cn.welsione.ascoder.analysis.CodeGraphWorkspaceContext;
import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import cn.welsione.ascoder.analysis.FileInspectionTools;
import cn.welsione.jprompt.loader.ClasspathTemplateLoader;
import cn.welsione.jprompt.loader.CompositeTemplateLoader;
import cn.welsione.jprompt.loader.TemplateLoader;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileInspectionToolsTests {

    private Path repoRoot;
    private FileInspectionTools tools;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        this.repoRoot = tempDir;
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.writeString(tempDir.resolve("src/main/java/Hello.java"), """
                package demo;
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("hi");
                    }
                }
                """);
        Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(tempDir.resolve("docs/README.md"), "# Title\n\nHello world.");
        Files.createDirectories(tempDir.resolve("config"));
        Files.writeString(tempDir.resolve("config/app.yml"), "key: value\n");

        CodeGraphWorkspaceContext ctx = CodeGraphWorkspaceContext.single(repoRoot);
        AgentConfigService agentConfigService = mock(AgentConfigService.class);
        when(agentConfigService.getByAgentId("self-learning-insight")).thenReturn(Optional.empty());
        TemplateLoader noopFallback = path -> { throw new cn.welsione.jprompt.TemplateException("No fallback: " + path); };
        CompositeTemplateLoader loader = new CompositeTemplateLoader(new ClasspathTemplateLoader(), noopFallback);
        PromptManager promptManager = new PromptManager(agentConfigService, loader, false);
        tools = new FileInspectionTools(ctx, new AtomicReference<>(""), "ProjectSpace: test", promptManager);
    }

    // ===== registerAll =====

    @Test
    void registerAllRegistersFourToolMethods() {
        Toolkit toolkit = new Toolkit();
        tools.registerAll(toolkit);

        assertThat(toolkit.getToolNames())
                .containsExactlyInAnyOrder(
                        "file_read", "file_list", "file_info", "file_glob"
                );
    }

    // ===== file_read =====

    @Test
    void fileReadReturnsContentWithLineNumbers() {
        ToolResultBlock result = tools.readFile("src/main/java/Hello.java", null, null, null, null).block();

        String text = extractText(result);
        assertThat(text)
                .contains("Reading File: src/main/java/Hello.java")
                .contains("1: package demo;")
                .contains("2: public class Hello {");
    }

    @Test
    void fileReadRespectsLineRange() {
        ToolResultBlock result = tools.readFile("src/main/java/Hello.java", 3, 4, null, null).block();

        String text = extractText(result);
        assertThat(text)
                .contains("3:     public static void main")
                .contains("4:         System.out.println")
                .doesNotContain("1: package demo;");
    }

    @Test
    void fileReadRejectsPathTraversal() {
        ToolResultBlock result = tools.readFile("../etc/passwd", null, null, null, null).block();
        assertThat(extractText(result)).contains("..");
    }

    @Test
    void fileReadReturnsErrorForMissingFile() {
        ToolResultBlock result = tools.readFile("nonexistent.java", null, null, null, null).block();
        assertThat(extractText(result)).contains("Not a regular file");
    }

    @Test
    void fileReadReturnsErrorWhenFileTooLarge() throws Exception {
        Files.writeString(repoRoot.resolve("big.txt"), "x".repeat(10_000));
        ToolResultBlock result = tools.readFile("big.txt", null, null, 1000L, null).block();
        String text = extractText(result);
        assertThat(text)
                .contains("File too large")
                .contains("exceeds maxBytes")
                .doesNotContain("Reading File:");
    }

    @Test
    void fileReadReturnsErrorWhenStartLineGreaterThanEndLine() {
        ToolResultBlock result = tools.readFile("src/main/java/Hello.java", 10, 5, null, null).block();
        String text = extractText(result);
        assertThat(text)
                .contains("Invalid line range")
                .doesNotContain("Reading File:");
    }

    @Test
    void fileReadTruncatesLongLines() throws Exception {
        String longLine = "x".repeat(5000);
        Files.writeString(repoRoot.resolve("long.txt"), "short\n" + longLine + "\nend");
        ToolResultBlock result = tools.readFile("long.txt", null, null, null, null).block();
        String text = extractText(result);
        assertThat(text)
                .contains("line truncated")
                .doesNotContain(longLine);
    }

    // ===== file_list =====

    @Test
    void fileListReturnsEntriesWithMetadata() {
        ToolResultBlock result = tools.listFiles(".", null, null, null).block();

        String text = extractText(result);
        assertThat(text)
                .contains("Directory: .")
                .contains("[file]")
                .contains("[dir]")
                .contains("src/main/java/Hello.java")
                .contains("docs/README.md");
    }

    @Test
    void fileListRespectsMaxDepth() throws Exception {
        Files.createDirectories(repoRoot.resolve("a/b/c/d"));
        Files.writeString(repoRoot.resolve("a/b/c/d/deep.txt"), "x");
        ToolResultBlock result = tools.listFiles(".", 1, null, null).block();
        // depth=1 只看一层，a/b/c/d 不应出现
        assertThat(extractText(result)).doesNotContain("a/b/c/d/deep.txt");
    }

    @Test
    void fileListRejectsPathTraversal() {
        ToolResultBlock result = tools.listFiles("../etc", null, null, null).block();
        assertThat(extractText(result)).contains("..");
    }

    // ===== file_info =====

    @Test
    void fileInfoReturnsMetadata() {
        ToolResultBlock result = tools.fileInfo("docs/README.md", null).block();

        String text = extractText(result);
        assertThat(text)
                .contains("File Info: docs/README.md")
                .contains("- **Type**: file")
                .contains("- **Size**:")
                .contains("- **Regular file**: yes");
    }

    @Test
    void fileInfoIdentifiesDirectory() {
        ToolResultBlock result = tools.fileInfo("src", null).block();
        String text = extractText(result);
        assertThat(text)
                .contains("Type**: directory")
                .contains("Regular file**: no");
    }

    // ===== file_glob =====

    @Test
    void fileGlobFindsMatchingFiles() {
        ToolResultBlock result = tools.globFiles("**/*.java", null, null, null).block();

        String text = extractText(result);
        assertThat(text)
                .contains("Found")
                .contains("Hello.java");
    }

    @Test
    void fileGlobReturnsEmptyForNoMatch() {
        ToolResultBlock result = tools.globFiles("**/*.ts", null, null, null).block();
        assertThat(extractText(result)).contains("No files matched");
    }

    @Test
    void fileGlobRejectsPathTraversal() {
        ToolResultBlock result = tools.globFiles("../*.java", null, null, null).block();
        assertThat(extractText(result)).contains("..");
    }

    // ===== 辅助 =====

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

    /** 小工具：简化 @TempDir lambda 调用。 */
    static final class ToolsWithOptions {
        static void check(java.util.function.Supplier<Void> action) {
            action.get();
        }
    }
}
