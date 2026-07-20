package cn.welsione.ascoder.analysis;

import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.TextUtil;
import io.agentscope.core.message.ToolResultBlock;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CodeGraph 工具辅助类，提供结果格式化、去重守卫和 codeContext 累积逻辑。
 *
 * <p>去重机制采用"延迟记录"策略：{@link #checkDuplicate(String, String)} 仅检查不记录，
 * {@link #toToolResult(String, String, CodeGraphToolResult)} 在工具成功后才调用
 * {@link #recordKey(String, String)} 记录去重键，使首次失败的调用可被重试。</p>
 */
@Slf4j
public class CodeGraphToolSupport {

    private static final int MAX_RESULT_LENGTH = 8000;

    private final AtomicReference<String> codeContext;
    private final String metadata;
    private final Set<String> executedCalls = ConcurrentHashMap.newKeySet();

    public CodeGraphToolSupport(AtomicReference<String> codeContext) {
        this(codeContext, "");
    }

    public CodeGraphToolSupport(AtomicReference<String> codeContext, String metadata) {
        this.codeContext = codeContext;
        this.metadata = metadata == null ? "" : metadata;
    }

    /**
     * 检查是否为重复调用。如果是重复调用，返回警告 ToolResultBlock；否则返回 null。
     * 仅检查不记录，去重键在 {@link #toToolResult} 成功后通过 {@link #recordKey} 记录。
     */
    public ToolResultBlock checkDuplicate(String toolName, String dedupeKey) {
        String key = dedupeKey(toolName, dedupeKey);
        if (executedCalls.contains(key)) {
            log.warn("检测到重复工具调用，已拦截：tool={}，dedupeKey={}", toolName, dedupeKey);
            return ToolResultBlock.text(
                    "⚠ Duplicate call intercepted: you already called " + toolName
                            + " with the same parameters. Use the existing result or try different parameters."
            );
        }
        return null;
    }

    /**
     * 记录去重键，供 {@link #toToolResult} 在工具成功后调用。
     */
    public void recordKey(String toolName, String dedupeKey) {
        String key = dedupeKey(toolName, dedupeKey);
        executedCalls.add(key);
    }

    /**
     * 清理 ANSI 转义码并截断过长结果。
     */
    public String sanitize(String output) {
        if (output == null || output.isBlank()) {
            return output;
        }
        String cleaned = TextUtil.stripAnsi(output);
        if (cleaned.length() > MAX_RESULT_LENGTH) {
            return cleaned.substring(0, MAX_RESULT_LENGTH)
                    + "\n\n... (result truncated, total " + output.length() + " chars, showing first " + MAX_RESULT_LENGTH + ")";
        }
        return cleaned;
    }

    /**
     * 将 CodeGraphToolResult 转为 ToolResultBlock，并累积到 codeContext。
     * 成功时记录去重键（延迟记录策略）。
     */
    public ToolResultBlock toToolResult(String toolName, String dedupeKey, CodeGraphToolResult result) {
        String rawOutput = result.getOutput();
        append(toolName, dedupeKey, rawOutput);
        if (!result.isSuccess()) {
            log.warn("工具 {} 查询失败，dedupeKey={}，错误={}", toolName, dedupeKey, rawOutput);
            return ToolResultBlock.error(toolName + " query failed:\n" + sanitize(rawOutput));
        }
        String cleaned = sanitize(rawOutput);
        log.debug("工具 {} 查询成功，dedupeKey={}，输出长度={}，清理后长度={}", toolName, dedupeKey, rawOutput.length(), cleaned.length());
        // 成功且内容非空时记录去重键
        if (cleaned != null && !cleaned.isEmpty()) {
            recordKey(toolName, dedupeKey);
        }
        return ToolResultBlock.text(cleaned);
    }

    private String dedupeKey(String toolName, String query) {
        return toolName + "|" + (query == null ? "" : query.trim());
    }

    private void append(String toolName, String query, String output) {
        String block = """

                ## CodeGraph Tool Result

                Tool: %s
                Query: %s
                %s

                %s
                """.formatted(
                toolName,
                query == null || query.isBlank() ? "(empty)" : query,
                metadata.isBlank() ? "" : "\n" + metadata,
                output
        );
        codeContext.updateAndGet(current -> current == null || current.isBlank() ? block.stripLeading() : current + block);
    }
}
