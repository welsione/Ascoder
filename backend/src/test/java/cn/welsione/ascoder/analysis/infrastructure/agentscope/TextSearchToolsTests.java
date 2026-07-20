package cn.welsione.ascoder.analysis.infrastructure.agentscope;

import cn.welsione.ascoder.analysis.CodeGraphWorkspaceContext;
import cn.welsione.ascoder.analysis.TextSearchTools;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TextSearchToolsTests {

    private Path repoRoot;
    private TextSearchTools tools;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        this.repoRoot = tempDir;
        Files.writeString(tempDir.resolve("Service.java"), """
                package demo;
                public class Service {
                    public void login() {
                        System.out.println("login called");
                    }
                    public void logout() {
                        // logout method
                    }
                }
                """);
        Files.writeString(tempDir.resolve("README.md"), """
                # Project
                login and logout are exposed.
                """);
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/Helper.java"), """
                package demo;
                public class Helper {
                    public void login() {}
                }
                """);

        CodeGraphWorkspaceContext ctx = CodeGraphWorkspaceContext.single(repoRoot);
        tools = new TextSearchTools(ctx, new AtomicReference<>(""), "ProjectSpace: test");
    }

    @Test
    void textSearchFindsMatchesAcrossFiles() {
        ToolResultBlock result = tools.searchText("login", null, null, null, null).block();

        String text = extractText(result);
        // "login" 出现在：Service.java:3、Service.java:4、README.md:2、Helper.java:3
        assertThat(text)
                .contains("Service.java:3:")
                .contains("Service.java:4:")
                .contains("sub/Helper.java:3:")
                .contains("README.md:2:")
                .doesNotContain("logout method");
    }

    @Test
    void textSearchRespectsIncludeGlob() {
        // **/*.java 跨目录匹配 .java 文件
        ToolResultBlock result = tools.searchText("login", null, "**/*.java", null, null).block();

        String text = extractText(result);
        // glob 限制为 .java，所以 README.md 不应出现
        assertThat(text)
                .contains("Service.java:3:")
                .contains("sub/Helper.java:3:")
                .doesNotContain("README.md:");
    }

    @Test
    void textSearchRejectsInvalidRegex() {
        ToolResultBlock result = tools.searchText("[invalid", null, null, null, null).block();
        assertThat(extractText(result)).contains("Invalid regex pattern");
    }

    @Test
    void textSearchRejectsPathTraversal() {
        ToolResultBlock result = tools.searchText("anything", "../etc", null, null, null).block();
        assertThat(extractText(result)).contains("..");
    }

    @Test
    void textCountReturnsPerFileCounts() {
        ToolResultBlock result = tools.countText("login", null, null, null).block();

        String text = extractText(result);
        // Service.java 有 2 处（login called, logout method）— 不，logout 不含 login
        // 实际：Service.java 1 处（System.out），Helper.java 1 处，README.md 1 处
        assertThat(text)
                .contains("Service.java:")
                .contains("README.md:")
                .contains("sub/Helper.java:");
    }

    @Test
    void textCountReturnsEmptyForNoMatch() {
        ToolResultBlock result = tools.countText("nonexistent_token_xyz", null, null, null).block();
        assertThat(extractText(result)).contains("No files matched");
    }

    @Test
    void textGrepLinesShowsContext() {
        ToolResultBlock result = tools.grepLines("public", "Service.java", null, null).block();

        String text = extractText(result);
        // Service.java 中包含 "public" 的行：2, 3, 6（class/login/logout）
        assertThat(text)
                .contains("Service.java:2:")
                .contains("Service.java:3:")
                .contains("Service.java:6:");
    }

    @Test
    void textGrepLinesRejectsMissingFile() {
        ToolResultBlock result = tools.grepLines("any", "nonexistent.java", null, null).block();
        assertThat(extractText(result)).contains("Not a regular file");
    }

    @Test
    void textGrepLinesRejectsPathTraversal() {
        ToolResultBlock result = tools.grepLines("any", "../etc/passwd", null, null).block();
        assertThat(extractText(result)).contains("..");
    }

    @Test
    void duplicateSearchIsIntercepted() {
        tools.searchText("login", null, null, null, null).block();
        ToolResultBlock result = tools.searchText("login", null, null, null, null).block();
        assertThat(extractText(result)).contains("Duplicate call intercepted");
    }

    @Test
    void binaryFileIsSkippedSilently() throws Exception {
        // 写一个含大量 null 字节的"二进制"文件
        byte[] binary = new byte[100];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) (i % 2 == 0 ? 0 : 0x20);
        }
        Files.write(repoRoot.resolve("blob.bin"), binary);
        // text_search 应当跳过它（不出现在结果中）
        ToolResultBlock result = tools.searchText("anything", null, null, null, null).block();
        String text = extractText(result);
        // 也不抛错
        assertThat(text).doesNotContain("blob.bin");
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
