package cn.welsione.ascoder.common.exception;

/**
 * 工具执行失败异常，对应 HTTP 502。
 * 用于 CodeGraph CLI、命令执行、外部工具调用失败等场景。
 */
public class ToolExecutionException extends DomainException {

    public ToolExecutionException(String message) {
        super("TOOL_ERROR", message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super("TOOL_ERROR", message, cause);
    }
}
