package cn.welsione.ascoder.codegraph.port;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * CodeGraph 工具调用的通用返回结果。
 * 使用 {@link #success(String)} 构造成功结果，{@link #error(String)} 构造失败结果。
 */
@Value
@AllArgsConstructor
public class CodeGraphToolResult {
    boolean success;
    String output;

    /**
     * 构造成功结果。
     *
     * @param content 工具输出的内容
     * @return success=true 的结果实例
     */
    public static CodeGraphToolResult success(String content) {
        return new CodeGraphToolResult(true, content);
    }

    /**
     * 构造失败结果，用于工具执行出错或参数校验失败的场景。
     *
     * @param message 错误信息，将反馈给 LLM
     * @return success=false 的结果实例
     */
    public static CodeGraphToolResult error(String message) {
        return new CodeGraphToolResult(false, message);
    }
}
