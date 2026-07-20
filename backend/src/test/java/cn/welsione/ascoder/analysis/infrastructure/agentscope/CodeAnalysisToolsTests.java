package cn.welsione.ascoder.analysis.infrastructure.agentscope;

import cn.welsione.ascoder.analysis.CodeAnalysisTools;
import cn.welsione.ascoder.analysis.CodeGraphWorkspaceContext;
import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeAnalysisToolsTests {

    private final CodeGraphClient codeGraphClient = mock(CodeGraphClient.class);
    private final Path repositoryPath = Path.of("/tmp/repo");
    private final AtomicReference<String> codeContext = new AtomicReference<>("");
    private final CodeGraphWorkspaceContext workspaceContext = CodeGraphWorkspaceContext.single(repositoryPath);
    private final CodeAnalysisTools tools = new CodeAnalysisTools(
            codeGraphClient, workspaceContext, "default-question", codeContext, "ProjectSpace: demo"
    );

    @Test
    void registerAllRegistersNineToolMethods() {
        Toolkit toolkit = new Toolkit();
        tools.registerAll(toolkit);

        assertThat(toolkit.getToolNames())
                .containsExactlyInAnyOrder(
                        "codegraph_context",
                        "codegraph_search",
                        "codegraph_files",
                        "codegraph_callers",
                        "codegraph_callees",
                        "codegraph_impact",
                        "codegraph_affected",
                        "codegraph_explore",
                        "codegraph_node"
                );
    }

    @Test
    void searchCodeGraphUsesDefaultQuestionWhenQueryIsBlank() {
        when(codeGraphClient.context(eq(repositoryPath), eq("default-question")))
                .thenReturn(new CodeGraphToolResult(true, "### Entry Points\n- main"));

        ToolResultBlock result = tools.searchCodeGraph("   ", null).block();

        assertThat(result).isNotNull();
        assertThat(extractText(result)).contains("### Entry Points");
        verify(codeGraphClient).context(repositoryPath, "default-question");
    }

    @Test
    void searchCodeGraphAppendsResultToCodeContext() {
        when(codeGraphClient.context(eq(repositoryPath), anyString()))
                .thenReturn(new CodeGraphToolResult(true, "### Entry Points\n- main"));

        tools.searchCodeGraph("how to start?", null).block();
        tools.searchCodeGraph("entry point?", null).block();

        assertThat(codeContext.get())
                .contains("## CodeGraph Tool Result")
                .contains("codegraph_context")
                .contains("### Entry Points");
    }

    @Test
    void searchRejectsEmptyQuery() {
        ToolResultBlock result = tools.search(null, null, 10, "method", null).block();

        assertThat(extractText(result)).contains("Error: codegraph_search requires query or search.");
        verify(codeGraphClient, never()).query(any(), any(), any(), any());
    }

    @Test
    void searchDelegatesToCodeGraphClient() {
        when(codeGraphClient.query(eq(repositoryPath), eq("FooService"), eq(5), eq("class")))
                .thenReturn(new CodeGraphToolResult(true, "- FooService"));

        ToolResultBlock result = tools.search("FooService", null, 5, "class", null).block();

        assertThat(extractText(result)).isEqualTo("- FooService");
    }

    @Test
    void searchAcceptsSearchAlias() {
        when(codeGraphClient.query(eq(repositoryPath), eq("Foo"), any(), any()))
                .thenReturn(new CodeGraphToolResult(true, "matched"));

        ToolResultBlock result = tools.search(null, "Foo", 10, null, null).block();

        assertThat(extractText(result)).isEqualTo("matched");
        verify(codeGraphClient).query(repositoryPath, "Foo", 10, null);
    }

    @Test
    void filesBuildsCompositeQueryForDedup() {
        when(codeGraphClient.files(eq(repositoryPath), anyString(), anyString(), anyString(), any()))
                .thenReturn(new CodeGraphToolResult(true, "tree"));

        tools.files("src/main", "**/*.java", "tree", 3, null).block();

        verify(codeGraphClient).files(repositoryPath, "src/main", "**/*.java", "tree", 3);
    }

    @Test
    void callersAcceptsQueryAlias() {
        when(codeGraphClient.callers(eq(repositoryPath), eq("handlePay"), any()))
                .thenReturn(new CodeGraphToolResult(true, "OrderController#create"));

        ToolResultBlock result = tools.callers(null, "handlePay", 20, null).block();

        assertThat(extractText(result)).isEqualTo("OrderController#create");
    }

    @Test
    void calleesRejectsEmptySymbol() {
        ToolResultBlock result = tools.callees("", null, 20, null).block();
        assertThat(extractText(result)).contains("Error: codegraph_callees requires symbol or query.");
        verify(codeGraphClient, never()).callees(any(), any(), any());
    }

    @Test
    void impactAcceptsDepth() {
        when(codeGraphClient.impact(eq(repositoryPath), eq("X"), eq(3)))
                .thenReturn(new CodeGraphToolResult(true, "impact-X"));

        ToolResultBlock result = tools.impact("X", null, 3, null).block();

        assertThat(extractText(result)).isEqualTo("impact-X");
    }

    @Test
    void affectedDelegatesFilesAndDepth() {
        when(codeGraphClient.affected(eq(repositoryPath), eq("a.java,b.java"), eq(2), any()))
                .thenReturn(new CodeGraphToolResult(true, "tests"));

        ToolResultBlock result = tools.affected("a.java,b.java", 2, "*Test*", null).block();

        assertThat(extractText(result)).isEqualTo("tests");
        verify(codeGraphClient).affected(repositoryPath, "a.java,b.java", 2, "*Test*");
    }

    @Test
    void duplicateCallsAreIntercepted() {
        when(codeGraphClient.context(eq(repositoryPath), anyString()))
                .thenReturn(new CodeGraphToolResult(true, "result"));

        tools.searchCodeGraph("same-query", null).block();
        tools.searchCodeGraph("same-query", null).block();

        verify(codeGraphClient, times(1)).context(repositoryPath, "same-query");
    }

    @Test
    void duplicateAcrossDifferentToolsShareSameContext() {
        when(codeGraphClient.context(eq(repositoryPath), anyString()))
                .thenReturn(new CodeGraphToolResult(true, "ctx-result"));
        when(codeGraphClient.query(eq(repositoryPath), anyString(), any(), any()))
                .thenReturn(new CodeGraphToolResult(true, "query-result"));

        tools.searchCodeGraph("shared", null).block();
        tools.search("shared", null, 10, null, null).block();

        verify(codeGraphClient).context(repositoryPath, "shared");
        verify(codeGraphClient).query(repositoryPath, "shared", 10, null);
        assertThat(codeContext.get())
                .contains("ctx-result")
                .contains("query-result");
    }

    @Test
    void contextSearchRetriesWithFallbackWhenInitialResultEmpty() {
        when(codeGraphClient.context(eq(repositoryPath), eq("入口")))
                .thenReturn(new CodeGraphToolResult(true, ""));
        when(codeGraphClient.context(eq(repositoryPath), eq("SpringBootApplication main AscoderApplication Application entry class")))
                .thenReturn(new CodeGraphToolResult(true, "### Entry Points\n- AscoderApplication"));

        ToolResultBlock result = tools.searchCodeGraph("入口", null).block();

        assertThat(extractText(result)).contains("### Entry Points");
        verify(codeGraphClient, times(2)).context(eq(repositoryPath), anyString());
    }

    @Test
    void contextSearchRecognizesExploreExplorationMarkerWithoutFallback() {
        when(codeGraphClient.context(eq(repositoryPath), eq("main functionality")))
                .thenReturn(new CodeGraphToolResult(true, "**Exploration:** main functionality\nFound 10 symbols."));

        ToolResultBlock result = tools.searchCodeGraph("main functionality", null).block();

        assertThat(extractText(result)).contains("**Exploration:**");
        // hasUsefulContext 识别 **Exploration:** 标记，不触发 fallback，只调用一次
        verify(codeGraphClient, times(1)).context(repositoryPath, "main functionality");
    }

    @Test
    void contextSearchRecognizesExploreSourceCodeMarkerWithoutFallback() {
        when(codeGraphClient.context(eq(repositoryPath), eq("auth service")))
                .thenReturn(new CodeGraphToolResult(true, "**Source Code**\npublic class AuthService"));

        ToolResultBlock result = tools.searchCodeGraph("auth service", null).block();

        assertThat(extractText(result)).contains("**Source Code**");
        verify(codeGraphClient, times(1)).context(repositoryPath, "auth service");
    }

    @Test
    void contextSearchRecognizesExploreBlastRadiusMarkerWithoutFallback() {
        when(codeGraphClient.context(eq(repositoryPath), eq("database connection")))
                .thenReturn(new CodeGraphToolResult(true, "**Blast radius** — what depends on these\n3 callers found."));

        ToolResultBlock result = tools.searchCodeGraph("database connection", null).block();

        assertThat(extractText(result)).contains("**Blast radius**");
        verify(codeGraphClient, times(1)).context(repositoryPath, "database connection");
    }

    @Test
    void extractTextHelperSupportsEmptyOutputs() {
        ToolResultBlock empty = new ToolResultBlock(null, null, List.of(), null);
        assertThat(extractText(empty)).isEmpty();
    }

    // --- Repository not found tests ---

    @Test
    void searchCodeGraphReturnsErrorWhenRepositoryNotFound() {
        CodeGraphWorkspaceContext multiRepoContext = buildMultiRepoContext();
        CodeAnalysisTools multiRepoTools = new CodeAnalysisTools(
                codeGraphClient, multiRepoContext, "default-question", codeContext, ""
        );

        ToolResultBlock result = multiRepoTools.searchCodeGraph("query", "nonexistent-repo").block();

        assertThat(extractText(result)).contains("Repository not found: nonexistent-repo");
        verify(codeGraphClient, never()).context(any(), anyString());
    }

    @Test
    void searchReturnsErrorWhenRepositoryNotFound() {
        CodeGraphWorkspaceContext multiRepoContext = buildMultiRepoContext();
        CodeAnalysisTools multiRepoTools = new CodeAnalysisTools(
                codeGraphClient, multiRepoContext, "default-question", codeContext, ""
        );

        ToolResultBlock result = multiRepoTools.search("FooService", null, 10, null, "nonexistent-repo").block();

        assertThat(extractText(result)).contains("Repository not found: nonexistent-repo");
        verify(codeGraphClient, never()).query(any(), any(), any(), any());
    }

    @Test
    void filesReturnsErrorWhenRepositoryNotFound() {
        CodeGraphWorkspaceContext multiRepoContext = buildMultiRepoContext();
        CodeAnalysisTools multiRepoTools = new CodeAnalysisTools(
                codeGraphClient, multiRepoContext, "default-question", codeContext, ""
        );

        ToolResultBlock result = multiRepoTools.files(null, null, null, null, "nonexistent-repo").block();

        assertThat(extractText(result)).contains("Repository not found: nonexistent-repo");
        verify(codeGraphClient, never()).files(any(), any(), any(), any(), any());
    }

    @Test
    void callersReturnsErrorWhenRepositoryNotFound() {
        CodeGraphWorkspaceContext multiRepoContext = buildMultiRepoContext();
        CodeAnalysisTools multiRepoTools = new CodeAnalysisTools(
                codeGraphClient, multiRepoContext, "default-question", codeContext, ""
        );

        ToolResultBlock result = multiRepoTools.callers("X", null, 20, "nonexistent-repo").block();

        assertThat(extractText(result)).contains("Repository not found: nonexistent-repo");
        verify(codeGraphClient, never()).callers(any(), any(), any());
    }

    @Test
    void calleesReturnsErrorWhenRepositoryNotFound() {
        CodeGraphWorkspaceContext multiRepoContext = buildMultiRepoContext();
        CodeAnalysisTools multiRepoTools = new CodeAnalysisTools(
                codeGraphClient, multiRepoContext, "default-question", codeContext, ""
        );

        ToolResultBlock result = multiRepoTools.callees("X", null, 20, "nonexistent-repo").block();

        assertThat(extractText(result)).contains("Repository not found: nonexistent-repo");
        verify(codeGraphClient, never()).callees(any(), any(), any());
    }

    @Test
    void impactReturnsErrorWhenRepositoryNotFound() {
        CodeGraphWorkspaceContext multiRepoContext = buildMultiRepoContext();
        CodeAnalysisTools multiRepoTools = new CodeAnalysisTools(
                codeGraphClient, multiRepoContext, "default-question", codeContext, ""
        );

        ToolResultBlock result = multiRepoTools.impact("X", null, 2, "nonexistent-repo").block();

        assertThat(extractText(result)).contains("Repository not found: nonexistent-repo");
        verify(codeGraphClient, never()).impact(any(), any(), any());
    }

    @Test
    void affectedReturnsErrorWhenRepositoryNotFound() {
        CodeGraphWorkspaceContext multiRepoContext = buildMultiRepoContext();
        CodeAnalysisTools multiRepoTools = new CodeAnalysisTools(
                codeGraphClient, multiRepoContext, "default-question", codeContext, ""
        );

        ToolResultBlock result = multiRepoTools.affected("a.java", 5, null, "nonexistent-repo").block();

        assertThat(extractText(result)).contains("Repository not found: nonexistent-repo");
        verify(codeGraphClient, never()).affected(any(), any(), any(), any());
    }

    @Test
    void validRepositoryNameDoesNotThrow() {
        CodeGraphWorkspaceContext multiRepoContext = buildMultiRepoContext();
        CodeAnalysisTools multiRepoTools = new CodeAnalysisTools(
                codeGraphClient, multiRepoContext, "default-question", codeContext, ""
        );
        when(codeGraphClient.query(eq(repositoryPath), eq("X"), any(), any()))
                .thenReturn(new CodeGraphToolResult(true, "result"));

        ToolResultBlock result = multiRepoTools.search("X", null, 10, null, "primary-repo").block();

        assertThat(extractText(result)).isEqualTo("result");
        verify(codeGraphClient).query(repositoryPath, "X", 10, null);
    }

    // --- Blank output (no index) tests ---

    @Test
    void searchReturnsNoIndexMessageWhenOutputIsBlank() {
        when(codeGraphClient.query(eq(repositoryPath), eq("X"), any(), any()))
                .thenReturn(new CodeGraphToolResult(true, ""));

        ToolResultBlock result = tools.search("X", null, 10, null, null).block();

        assertThat(extractText(result)).contains("No CodeGraph index available for this project");
    }

    @Test
    void searchReturnsNoIndexMessageWhenOutputIsNull() {
        when(codeGraphClient.query(eq(repositoryPath), eq("X"), any(), any()))
                .thenReturn(new CodeGraphToolResult(true, null));

        ToolResultBlock result = tools.search("X", null, 10, null, null).block();

        assertThat(extractText(result)).contains("No CodeGraph index available for this project");
    }

    @Test
    void contextReturnsNoIndexMessageWhenOutputIsBlank() {
        when(codeGraphClient.context(eq(repositoryPath), anyString()))
                .thenReturn(new CodeGraphToolResult(true, ""));

        ToolResultBlock result = tools.searchCodeGraph("query", null).block();

        assertThat(extractText(result)).contains("No CodeGraph index available for this project");
    }

    @Test
    void filesReturnsNoIndexMessageWhenOutputIsBlank() {
        when(codeGraphClient.files(eq(repositoryPath), any(), any(), any(), any()))
                .thenReturn(new CodeGraphToolResult(true, ""));

        ToolResultBlock result = tools.files(null, null, null, null, null).block();

        assertThat(extractText(result)).contains("No CodeGraph index available for this project");
    }

    @Test
    void callersReturnsNoIndexMessageWhenOutputIsBlank() {
        when(codeGraphClient.callers(eq(repositoryPath), eq("X"), any()))
                .thenReturn(new CodeGraphToolResult(true, ""));

        ToolResultBlock result = tools.callers("X", null, 20, null).block();

        assertThat(extractText(result)).contains("No CodeGraph index available for this project");
    }

    @Test
    void calleesReturnsNoIndexMessageWhenOutputIsBlank() {
        when(codeGraphClient.callees(eq(repositoryPath), eq("X"), any()))
                .thenReturn(new CodeGraphToolResult(true, ""));

        ToolResultBlock result = tools.callees("X", null, 20, null).block();

        assertThat(extractText(result)).contains("No CodeGraph index available for this project");
    }

    @Test
    void impactReturnsNoIndexMessageWhenOutputIsBlank() {
        when(codeGraphClient.impact(eq(repositoryPath), eq("X"), any()))
                .thenReturn(new CodeGraphToolResult(true, ""));

        ToolResultBlock result = tools.impact("X", null, 2, null).block();

        assertThat(extractText(result)).contains("No CodeGraph index available for this project");
    }

    @Test
    void affectedReturnsNoIndexMessageWhenOutputIsBlank() {
        when(codeGraphClient.affected(eq(repositoryPath), any(), any(), any()))
                .thenReturn(new CodeGraphToolResult(true, ""));

        ToolResultBlock result = tools.affected("a.java", 5, null, null).block();

        assertThat(extractText(result)).contains("No CodeGraph index available for this project");
    }

    @Test
    void failedResultIsNotReplacedByNoIndexMessage() {
        when(codeGraphClient.query(eq(repositoryPath), eq("X"), any(), any()))
                .thenReturn(new CodeGraphToolResult(false, "CLI error"));

        ToolResultBlock result = tools.search("X", null, 10, null, null).block();

        assertThat(extractText(result)).contains("CLI error");
        assertThat(extractText(result)).doesNotContain("No CodeGraph index available");
    }

    private CodeGraphWorkspaceContext buildMultiRepoContext() {
        AgentRequest.RepositoryContext primaryRepo = new AgentRequest.RepositoryContext(
                1L, "primary-repo", null, null, null,
                repositoryPath.toString(), null, "backend", true
        );
        return new CodeGraphWorkspaceContext(repositoryPath, List.of(primaryRepo));
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
