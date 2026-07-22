package cn.welsione.ascoder.question.stream;

/**
 * 流式错误分类工具，根据错误消息文本归类到固定 category，便于前端针对性展示。
 */
final class StreamErrorClassifier {

    private StreamErrorClassifier() {}

    static String classify(String message) {
        if (message == null) {
            return "unknown";
        }
        if (message.contains("任务被中断") || message.contains("interrupted") || message.contains("Interrupted")) {
            return "interrupted";
        }
        if (message.contains("Maximum iterations")) {
            return "max_iters_exhausted";
        }
        if (message.contains("Retries exhausted")) {
            return "model_timeout";
        }
        if (message.contains("Model request timeout")) {
            return "model_timeout";
        }
        if (message.contains("tool timeout") || message.contains("Tool execution timeout")) {
            return "tool_timeout";
        }
        if (message.contains("repository") && (message.contains("not found") || message.contains("resolution"))) {
            return "codegraph_repository_error";
        }
        if (message.contains("index") && (message.contains("missing") || message.contains("not found"))) {
            return "codegraph_index_missing";
        }
        if (message.contains("Error generating summary")) {
            return "summary_timeout";
        }
        if (message.contains("timeout") || message.contains("Timeout")) {
            return "timeout";
        }
        return "agent_error";
    }

    /**
     * 判断 Agent 给出的"回答"是否实为失败信息，依赖与 {@link #classify(String)} 一致的分类规则。
     */
    static boolean isAgentFailure(String answer) {
        String category = classify(answer);
        return category.equals("max_iters_exhausted")
                || category.equals("summary_timeout")
                || category.equals("model_timeout");
    }
}
