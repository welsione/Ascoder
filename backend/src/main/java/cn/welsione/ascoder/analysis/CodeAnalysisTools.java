package cn.welsione.ascoder.analysis;

import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 代码分析工具集，合并自原 CodeGraph*Tool 类。统一暴露 9 个 @Tool 方法，
 * 共用同一个 CodeGraphToolSupport（去重 + ANSI 清洗 + codeContext 累积）。
 * AgentScope 的 Toolkit 会通过反射扫描类内所有 @Tool 方法并自动注册。
 */
@Slf4j
public class CodeAnalysisTools {

    private static final String NO_INDEX_MESSAGE = "No CodeGraph index available for this project. Use file/git/text tools instead.";
    private static final int DEFAULT_EXPLORE_MAX_FILES = 20;

    private final CodeGraphClient codeGraphClient;
    private final CodeGraphWorkspaceContext workspaceContext;
    private final String defaultQuestion;
    private final CodeGraphToolSupport support;

    public CodeAnalysisTools(
            CodeGraphClient codeGraphClient,
            CodeGraphWorkspaceContext workspaceContext,
            String defaultQuestion,
            AtomicReference<String> codeContext,
            String metadata
    ) {
        this.codeGraphClient = codeGraphClient;
        this.workspaceContext = workspaceContext;
        this.defaultQuestion = defaultQuestion;
        this.support = new CodeGraphToolSupport(codeContext, metadata);
    }

    /**
     * 一次性注册类内所有 @Tool 方法到 Toolkit。
     */
    public void registerAll(Toolkit toolkit) {
        toolkit.registration().tool(this).apply();
        log.debug("CodeAnalysisTools 注册完成，共 9 个 @Tool 方法");
    }

    @Tool(
            name = "codegraph_context",
            description = """
                    Compatibility wrapper for a broad CodeGraph query. Kept for backward compatibility
                    with skills and prompts that still reference this tool.
                    Prefer codegraph_explore for new questions — it returns richer output (source
                    code, caller/callee trails, blast radius) and is the recommended tool.
                    Use codegraph_context only when a skill, prompt, or external instruction
                    explicitly references it.
                    For Chinese questions, rewrite the query into likely code-oriented English keywords
                    or framework symbols before searching.
                    In multi-repository workspaces, pass repositoryName to inspect a non-primary repository.
                    """
    )
    public Mono<ToolResultBlock> searchCodeGraph(
            @ToolParam(
                    name = "query",
                    required = false,
                    description = "A concise code-oriented search query, preferably with likely class, method, annotation, or framework names."
            ) String query,
            @ToolParam(
                    name = "repositoryName",
                    required = false,
                    description = "Optional repository name, role, repository id, or branch workspace id from AvailableRepositories. Defaults to primary repository."
            ) String repositoryName
    ) {
        return Mono.fromCallable(() -> {
            String effectiveQuery = query == null || query.isBlank() ? defaultQuestion : query;
            ToolResultBlock duplicate = support.checkDuplicate("codegraph_context", effectiveQuery);
            if (duplicate != null) {
                return duplicate;
            }
            try {
                Path repositoryPath = workspaceContext.resolveCodeGraphPath(repositoryName);
                CodeGraphToolResult result = searchWithFallback(repositoryPath, effectiveQuery);
                return support.toToolResult(
                        "codegraph_context",
                        effectiveQuery,
                        replaceBlankOutput(result)
                );
            } catch (IllegalArgumentException e) {
                log.warn("codegraph_context 仓库不存在：repositoryName={}", repositoryName);
                return support.toToolResult(
                        "codegraph_context",
                        effectiveQuery,
                        CodeGraphToolResult.error("Repository not found: " + repositoryName)
                );
            }
        });
    }

    @Tool(
            name = "codegraph_search",
            description = """
                    Search indexed symbols by name or keyword. Use this before deeper tools when you need
                    to find the exact class, method, function, interface, route, or component name.
                    In multi-repository workspaces, pass repositoryName to search a non-primary repository.
                    """
    )
    public Mono<ToolResultBlock> search(
            @ToolParam(name = "query", required = false, description = "Symbol name, partial name, or code keyword. Prefer this parameter.") String query,
            @ToolParam(name = "search", required = false, description = "Compatibility alias for query.") String search,
            @ToolParam(name = "limit", required = false, description = "Maximum results. Default is 10.") Integer limit,
            @ToolParam(name = "kind", required = false, description = "Optional symbol kind filter, such as class, method, function, interface, route, component.") String kind,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id from AvailableRepositories. Defaults to primary repository.") String repositoryName
    ) {
        String actualQuery = query == null || query.isBlank() ? search : query;
        if (actualQuery == null || actualQuery.isBlank()) {
            return Mono.just(ToolResultBlock.error("codegraph_search requires query or search."));
        }
        String dedupeKey = actualQuery + "|" + (kind == null ? "" : kind) + "|" + (limit == null ? "" : limit);
        ToolResultBlock duplicate = support.checkDuplicate("codegraph_search", dedupeKey);
        if (duplicate != null) {
            return Mono.just(duplicate);
        }
        return Mono.fromCallable(() -> {
            try {
                Path repositoryPath = workspaceContext.resolveCodeGraphPath(repositoryName);
                CodeGraphToolResult result = codeGraphClient.query(repositoryPath, actualQuery, limit, kind);
                return support.toToolResult("codegraph_search", dedupeKey, replaceBlankOutput(result));
            } catch (IllegalArgumentException e) {
                log.warn("codegraph_search 仓库不存在：repositoryName={}", repositoryName);
                return support.toToolResult("codegraph_search", dedupeKey, CodeGraphToolResult.error("Repository not found: " + repositoryName));
            }
        });
    }

    @Tool(
            name = "codegraph_files",
            description = """
                    Show indexed project file structure. Use this to understand project layout,
                    locate modules, or choose where to search next.
                    In multi-repository workspaces, pass repositoryName to inspect a non-primary repository.
                    """
    )
    public Mono<ToolResultBlock> files(
            @ToolParam(name = "filter", required = false, description = "Optional directory prefix filter.") String filter,
            @ToolParam(name = "pattern", required = false, description = "Optional glob pattern, such as **/*.java.") String pattern,
            @ToolParam(name = "format", required = false, description = "Output format: tree, flat, or grouped. Default is tree.") String format,
            @ToolParam(name = "maxDepth", required = false, description = "Maximum tree depth.") Integer maxDepth,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id from AvailableRepositories. Defaults to primary repository.") String repositoryName
    ) {
        String filesQuery = "%s %s".formatted(filter == null ? "" : filter, pattern == null ? "" : pattern).trim();
        String dedupeKey = filesQuery + "|" + (format == null ? "" : format) + "|" + (maxDepth == null ? "" : maxDepth);
        ToolResultBlock duplicate = support.checkDuplicate("codegraph_files", dedupeKey);
        if (duplicate != null) {
            return Mono.just(duplicate);
        }
        return Mono.fromCallable(() -> {
            try {
                Path repositoryPath = workspaceContext.resolveCodeGraphPath(repositoryName);
                CodeGraphToolResult result = codeGraphClient.files(repositoryPath, filter, pattern, format, maxDepth);
                return support.toToolResult("codegraph_files", dedupeKey, replaceBlankOutput(result));
            } catch (IllegalArgumentException e) {
                log.warn("codegraph_files 仓库不存在：repositoryName={}", repositoryName);
                return support.toToolResult("codegraph_files", dedupeKey, CodeGraphToolResult.error("Repository not found: " + repositoryName));
            }
        });
    }

    @Tool(
            name = "codegraph_callers",
            description = """
                    Find functions or methods that call a symbol. Use this when the user asks
                    who invokes an API, service, method, handler, or business operation.
                    In multi-repository workspaces, pass repositoryName to inspect a non-primary repository.
                    """
    )
    public Mono<ToolResultBlock> callers(
            @ToolParam(name = "symbol", required = false, description = "Function, method, class, or symbol name to inspect.") String symbol,
            @ToolParam(name = "query", required = false, description = "Alias for symbol.") String query,
            @ToolParam(name = "limit", required = false, description = "Maximum results. Default is 20.") Integer limit,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id from AvailableRepositories. Defaults to primary repository.") String repositoryName
    ) {
        String actualSymbol = symbol == null || symbol.isBlank() ? query : symbol;
        if (actualSymbol == null || actualSymbol.isBlank()) {
            return Mono.just(ToolResultBlock.error("codegraph_callers requires symbol or query."));
        }
        String dedupeKey = actualSymbol + "|" + (limit == null ? "" : limit);
        ToolResultBlock duplicate = support.checkDuplicate("codegraph_callers", dedupeKey);
        if (duplicate != null) {
            return Mono.just(duplicate);
        }
        return Mono.fromCallable(() -> {
            try {
                Path repositoryPath = workspaceContext.resolveCodeGraphPath(repositoryName);
                CodeGraphToolResult result = codeGraphClient.callers(repositoryPath, actualSymbol, limit);
                return support.toToolResult("codegraph_callers", dedupeKey, replaceBlankOutput(result));
            } catch (IllegalArgumentException e) {
                log.warn("codegraph_callers 仓库不存在：repositoryName={}", repositoryName);
                return support.toToolResult("codegraph_callers", dedupeKey, CodeGraphToolResult.error("Repository not found: " + repositoryName));
            }
        });
    }

    @Tool(
            name = "codegraph_callees",
            description = """
                    Find functions or methods called by a symbol. Use this when the user asks
                    what a method does internally, which dependencies it calls, or how logic flows outward.
                    In multi-repository workspaces, pass repositoryName to inspect a non-primary repository.
                    """
    )
    public Mono<ToolResultBlock> callees(
            @ToolParam(name = "symbol", required = false, description = "Function, method, class, or symbol name to inspect.") String symbol,
            @ToolParam(name = "query", required = false, description = "Alias for symbol.") String query,
            @ToolParam(name = "limit", required = false, description = "Maximum results. Default is 20.") Integer limit,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id from AvailableRepositories. Defaults to primary repository.") String repositoryName
    ) {
        String actualSymbol = symbol == null || symbol.isBlank() ? query : symbol;
        if (actualSymbol == null || actualSymbol.isBlank()) {
            return Mono.just(ToolResultBlock.error("codegraph_callees requires symbol or query."));
        }
        String dedupeKey = actualSymbol + "|" + (limit == null ? "" : limit);
        ToolResultBlock duplicate = support.checkDuplicate("codegraph_callees", dedupeKey);
        if (duplicate != null) {
            return Mono.just(duplicate);
        }
        return Mono.fromCallable(() -> {
            try {
                Path repositoryPath = workspaceContext.resolveCodeGraphPath(repositoryName);
                CodeGraphToolResult result = codeGraphClient.callees(repositoryPath, actualSymbol, limit);
                return support.toToolResult("codegraph_callees", dedupeKey, replaceBlankOutput(result));
            } catch (IllegalArgumentException e) {
                log.warn("codegraph_callees 仓库不存在：repositoryName={}", repositoryName);
                return support.toToolResult("codegraph_callees", dedupeKey, CodeGraphToolResult.error("Repository not found: " + repositoryName));
            }
        });
    }

    @Tool(
            name = "codegraph_impact",
            description = """
                    Analyze which code may be affected by changing a symbol. Use this for impact analysis,
                    refactoring risk, regression scope, or modification planning.
                    In multi-repository workspaces, pass repositoryName to inspect a non-primary repository.
                    """
    )
    public Mono<ToolResultBlock> impact(
            @ToolParam(name = "symbol", required = false, description = "Function, method, class, or symbol name to analyze.") String symbol,
            @ToolParam(name = "query", required = false, description = "Alias for symbol.") String query,
            @ToolParam(name = "depth", required = false, description = "Traversal depth. Default is 2.") Integer depth,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id from AvailableRepositories. Defaults to primary repository.") String repositoryName
    ) {
        String actualSymbol = symbol == null || symbol.isBlank() ? query : symbol;
        if (actualSymbol == null || actualSymbol.isBlank()) {
            return Mono.just(ToolResultBlock.error("codegraph_impact requires symbol or query."));
        }
        String dedupeKey = actualSymbol + "|" + (depth == null ? "" : depth);
        ToolResultBlock duplicate = support.checkDuplicate("codegraph_impact", dedupeKey);
        if (duplicate != null) {
            return Mono.just(duplicate);
        }
        return Mono.fromCallable(() -> {
            try {
                Path repositoryPath = workspaceContext.resolveCodeGraphPath(repositoryName);
                CodeGraphToolResult result = codeGraphClient.impact(repositoryPath, actualSymbol, depth);
                return support.toToolResult("codegraph_impact", dedupeKey, replaceBlankOutput(result));
            } catch (IllegalArgumentException e) {
                log.warn("codegraph_impact 仓库不存在：repositoryName={}", repositoryName);
                return support.toToolResult("codegraph_impact", dedupeKey, CodeGraphToolResult.error("Repository not found: " + repositoryName));
            }
        });
    }

    @Tool(
            name = "codegraph_affected",
            description = """
                    Find tests affected by changed source files. Use this when the user asks what tests
                    to run after changing files, or which test scope is relevant.
                    In multi-repository workspaces, pass repositoryName to inspect a non-primary repository.
                    """
    )
    public Mono<ToolResultBlock> affected(
            @ToolParam(name = "files", required = true, description = "Changed file paths separated by comma or newline.") String files,
            @ToolParam(name = "depth", required = false, description = "Dependency traversal depth. Default is 5.") Integer depth,
            @ToolParam(name = "filter", required = false, description = "Optional test glob filter.") String filter,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id from AvailableRepositories. Defaults to primary repository.") String repositoryName
    ) {
        String dedupeKey = files + "|" + (depth == null ? "" : depth) + "|" + (filter == null ? "" : filter);
        ToolResultBlock duplicate = support.checkDuplicate("codegraph_affected", dedupeKey);
        if (duplicate != null) {
            return Mono.just(duplicate);
        }
        return Mono.fromCallable(() -> {
            try {
                Path repositoryPath = workspaceContext.resolveCodeGraphPath(repositoryName);
                CodeGraphToolResult result = codeGraphClient.affected(repositoryPath, files, depth, filter);
                return support.toToolResult("codegraph_affected", dedupeKey, replaceBlankOutput(result));
            } catch (IllegalArgumentException e) {
                log.warn("codegraph_affected 仓库不存在：repositoryName={}", repositoryName);
                return support.toToolResult("codegraph_affected", dedupeKey, CodeGraphToolResult.error("Repository not found: " + repositoryName));
            }
        });
    }

    @Tool(
            name = "codegraph_explore",
            description = """
                    Explore a code area: relevant symbols' source code and call paths in one shot.
                    This is the recommended tool for broad, source-level exploration — it returns
                    source code excerpts, caller/callee trails, and blast radius analysis in a
                    single call, with no need to first call codegraph_search or codegraph_callers.
                    Use this for questions about code structure, execution flow, entry points,
                    module organization, cross-cutting concerns, or "where does X live".
                    Prefer this over the older codegraph_context tool.
                    In multi-repository workspaces, pass repositoryName to inspect a non-primary repository.
                    """
    )
    public Mono<ToolResultBlock> explore(
            @ToolParam(name = "query", required = true, description = "A concise code-oriented query describing the area to explore, with likely class, method, or module names.") String query,
            @ToolParam(name = "maxFiles", required = false, description = "Maximum number of source files to include. Default is 20.") Integer maxFiles,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id from AvailableRepositories. Defaults to primary repository.") String repositoryName
    ) {
        String dedupeKey = query + "|" + (maxFiles == null ? "" : maxFiles);
        ToolResultBlock duplicate = support.checkDuplicate("codegraph_explore", dedupeKey);
        if (duplicate != null) {
            return Mono.just(duplicate);
        }
        return Mono.fromCallable(() -> {
            try {
                Path repositoryPath = workspaceContext.resolveCodeGraphPath(repositoryName);
                CodeGraphToolResult result = codeGraphClient.explore(repositoryPath, query, maxFiles != null ? maxFiles : DEFAULT_EXPLORE_MAX_FILES);
                return support.toToolResult("codegraph_explore", dedupeKey, replaceBlankOutput(result));
            } catch (IllegalArgumentException e) {
                log.warn("codegraph_explore 仓库不存在：repositoryName={}", repositoryName);
                return support.toToolResult("codegraph_explore", dedupeKey, CodeGraphToolResult.error("Repository not found: " + repositoryName));
            }
        });
    }

    @Tool(
            name = "codegraph_node",
            description = """
                    Get one symbol's source code, caller chain, and callee chain.
                    Use this when you need detailed information about a specific class, method, or function —
                    its implementation, who calls it, and what it calls.
                    In multi-repository workspaces, pass repositoryName to inspect a non-primary repository.
                    """
    )
    public Mono<ToolResultBlock> node(
            @ToolParam(name = "name", required = true, description = "Exact or partial symbol name (class, method, function).") String name,
            @ToolParam(name = "file", required = false, description = "Optional file path to disambiguate a symbol or enter file mode.") String file,
            @ToolParam(name = "offset", required = false, description = "File mode: 1-based start line number.") Integer offset,
            @ToolParam(name = "limit", required = false, description = "File mode: maximum lines to include.") Integer limit,
            @ToolParam(name = "repositoryName", required = false, description = "Optional repository name, role, repository id, or branch workspace id from AvailableRepositories. Defaults to primary repository.") String repositoryName
    ) {
        String dedupeKey = name + "|" + (file == null ? "" : file) + "|" + (offset == null ? "" : offset) + "|" + (limit == null ? "" : limit);
        ToolResultBlock duplicate = support.checkDuplicate("codegraph_node", dedupeKey);
        if (duplicate != null) {
            return Mono.just(duplicate);
        }
        return Mono.fromCallable(() -> {
            try {
                Path repositoryPath = workspaceContext.resolveCodeGraphPath(repositoryName);
                CodeGraphToolResult result = codeGraphClient.node(repositoryPath, name, file, offset, limit);
                return support.toToolResult("codegraph_node", dedupeKey, replaceBlankOutput(result));
            } catch (IllegalArgumentException e) {
                log.warn("codegraph_node 仓库不存在：repositoryName={}", repositoryName);
                return support.toToolResult("codegraph_node", dedupeKey, CodeGraphToolResult.error("Repository not found: " + repositoryName));
            }
        });
    }

    private CodeGraphToolResult searchWithFallback(Path repositoryPath, String query) {
        CodeGraphToolResult result = codeGraphClient.context(repositoryPath, query);
        if (hasUsefulContext(result)) {
            return result;
        }

        String fallbackQuery = fallbackQuery(query);
        if (fallbackQuery.equals(query)) {
            return result;
        }

        log.debug("context 无有效结果，尝试 fallback 查询：{}", fallbackQuery);
        CodeGraphToolResult fallbackResult = codeGraphClient.context(repositoryPath, fallbackQuery);
        if (!result.isSuccess()) {
            return fallbackResult;
        }

        return new CodeGraphToolResult(
                fallbackResult.isSuccess(),
                result.getOutput() + "\n\n## Fallback CodeGraph Context\n\n" + fallbackResult.getOutput()
        );
    }

    private boolean hasUsefulContext(CodeGraphToolResult result) {
        if (!result.isSuccess() || result.getOutput() == null || result.getOutput().isBlank()) {
            return false;
        }
        String output = result.getOutput();
        return output.contains("### Entry Points")
                || output.contains("### Related Symbols")
                || output.contains("### Code")
                || output.contains("**Exploration:**")
                || output.contains("**Blast radius**")
                || output.contains("**Source Code**");
    }

    private String fallbackQuery(String query) {
        if (query.contains("入口") || query.toLowerCase().contains("entry")) {
            return "SpringBootApplication main AscoderApplication Application entry class";
        }
        if (query.contains("调用") || query.contains("流程") || query.toLowerCase().contains("flow")) {
            return query + " call flow service controller";
        }
        return query;
    }

    /**
     * 当 CodeGraph 返回空输出时，替换为无索引提示信息。
     */
    private CodeGraphToolResult replaceBlankOutput(CodeGraphToolResult result) {
        if (result.isSuccess() && (result.getOutput() == null || result.getOutput().isBlank())) {
            return CodeGraphToolResult.success(NO_INDEX_MESSAGE);
        }
        return result;
    }
}
