package cn.welsione.ascoder.question.stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link QuestionStreamService} 中纯函数分支的行为，主要覆盖错误分类与 summary 累积。
 */
class QuestionStreamServiceTests {

    @Test
    void classifyError_recognizesMaxIters() {
        assertThat(QuestionStreamService.classifyError("Maximum iterations reached"))
                .isEqualTo("max_iters_exhausted");
    }

    @Test
    void classifyError_recognizesModelTimeout() {
        assertThat(QuestionStreamService.classifyError("Model request timeout after 30s"))
                .isEqualTo("model_timeout");
    }

    @Test
    void classifyError_recognizesToolTimeout() {
        assertThat(QuestionStreamService.classifyError("Tool execution timeout"))
                .isEqualTo("tool_timeout");
        assertThat(QuestionStreamService.classifyError("tool timeout exceeded"))
                .isEqualTo("tool_timeout");
    }

    @Test
    void classifyError_recognizesCodegraphRepositoryError() {
        assertThat(QuestionStreamService.classifyError("repository not found: foo"))
                .isEqualTo("codegraph_repository_error");
        assertThat(QuestionStreamService.classifyError("repository resolution failed"))
                .isEqualTo("codegraph_repository_error");
    }

    @Test
    void classifyError_recognizesIndexMissing() {
        assertThat(QuestionStreamService.classifyError("index missing for project"))
                .isEqualTo("codegraph_index_missing");
        assertThat(QuestionStreamService.classifyError("index not found"))
                .isEqualTo("codegraph_index_missing");
    }

    @Test
    void classifyError_recognizesSummaryTimeout() {
        assertThat(QuestionStreamService.classifyError("Error generating summary: timeout"))
                .isEqualTo("summary_timeout");
    }

    @Test
    void classifyError_recognizesInterrupted() {
        assertThat(QuestionStreamService.classifyError("任务被中断：服务关闭，流式任务已中断"))
                .isEqualTo("interrupted");
        assertThat(QuestionStreamService.classifyError("Interrupted while waiting"))
                .isEqualTo("interrupted");
    }

    @Test
    void classifyError_fallbackToGenericTimeoutThenAgentError() {
        assertThat(QuestionStreamService.classifyError("Some Timeout happened"))
                .isEqualTo("timeout");
        assertThat(QuestionStreamService.classifyError("totally unrelated boom"))
                .isEqualTo("agent_error");
    }

    @Test
    void classifyError_nullMessageReturnsUnknown() {
        assertThat(QuestionStreamService.classifyError(null)).isEqualTo("unknown");
    }

    @Test
    void replaceSummary_replaceFlagOverwrites() {
        StringBuilder buf = new StringBuilder("old");
        QuestionStreamService.replaceSummary(buf, "new", true);
        assertThat(buf.toString()).isEqualTo("new");
    }

    @Test
    void replaceSummary_appendsWhenReplaceFalse() {
        StringBuilder buf = new StringBuilder("hello ");
        QuestionStreamService.replaceSummary(buf, "world", false);
        assertThat(buf.toString()).isEqualTo("hello world");
    }
}
