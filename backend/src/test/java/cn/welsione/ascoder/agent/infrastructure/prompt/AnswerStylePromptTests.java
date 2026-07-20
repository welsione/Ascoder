package cn.welsione.ascoder.agent.infrastructure.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 验证回答风格 prompt 模板中的流程图指引：必须是"酌情"而非"无条件"。
 */
class AnswerStylePromptTests {

    private String readTemplate(String path) throws Exception {
        return Files.readString(
                new ClassPathResource(path).getFile().toPath(),
                StandardCharsets.UTF_8
        );
    }

    @Test
    void developerStyleDoesNotUnconditionallyRequireFlowchart() throws Exception {
        String prompt = readTemplate("prompts/answer-style-developer.md");

        assertThat(prompt).doesNotContain("必须在结论之后、详细分析之前，用 Markdown 流程图");
    }

    @Test
    void productManagerStyleDoesNotUnconditionallyRequireFlowchart() throws Exception {
        String prompt = readTemplate("prompts/answer-style-product_manager.md");

        assertThat(prompt).doesNotContain("必须在结论之后、详细分析之前，用 Markdown 流程图");
    }

    @Test
    void testerStyleDoesNotUnconditionallyRequireFlowchart() throws Exception {
        String prompt = readTemplate("prompts/answer-style-tester.md");

        assertThat(prompt).doesNotContain("必须在结论之后、详细分析之前，用 Markdown 流程图");
    }

    @Test
    void answerStyleTemplatesDoNotShowStructuredResultFormat() throws Exception {
        String developer = readTemplate("prompts/answer-style-developer.md");
        String productManager = readTemplate("prompts/answer-style-product_manager.md");
        String tester = readTemplate("prompts/answer-style-tester.md");

        assertThat(developer).doesNotContain("回答结构：");
        assertThat(productManager).doesNotContain("回答结构：");
        assertThat(tester).doesNotContain("回答结构：");
    }

    @Test
    void answerStyleTemplatesPutEvidenceLast() throws Exception {
        String developer = readTemplate("prompts/answer-style-developer.md");
        String productManager = readTemplate("prompts/answer-style-product_manager.md");
        String tester = readTemplate("prompts/answer-style-tester.md");

        assertThat(developer).contains("最后");
        assertThat(productManager).contains("最后");
        assertThat(tester).contains("最后");
    }
}
