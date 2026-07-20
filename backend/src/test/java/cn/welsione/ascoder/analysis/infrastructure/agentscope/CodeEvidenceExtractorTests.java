package cn.welsione.ascoder.analysis.infrastructure.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import cn.welsione.ascoder.analysis.CodeEvidenceExtractor;
import org.junit.jupiter.api.Test;

class CodeEvidenceExtractorTests {

    private final CodeEvidenceExtractor extractor = new CodeEvidenceExtractor();

    @Test
    void extractsFileReferencesWithLineNumbersFromCodeGraphContext() {
        var evidence = extractor.extract("""
                - main (method) - backend/src/main/java/cn/welsione/ascoder/AscoderApplication.java:9
                - config - backend/src/main/resources/application.yml:6
                """);

        assertThat(evidence)
                .extracting("reference")
                .containsExactly(
                        "backend/src/main/java/cn/welsione/ascoder/AscoderApplication.java:9",
                        "backend/src/main/resources/application.yml:6"
                );
    }

    @Test
    void ignoresInternalBuiltinSkillDefinitionReferences() {
        var evidence = extractor.extract("""
                - springBootAnalysis - backend/src/main/java/cn/welsione/ascoder/skill/BuiltinSkillCatalog.java:21
                - main - backend/src/main/java/cn/welsione/ascoder/AscoderApplication.java:12
                """);

        assertThat(evidence)
                .extracting("reference")
                .containsExactly("backend/src/main/java/cn/welsione/ascoder/AscoderApplication.java:12");
    }

    @Test
    void extractsReferencesFromMarkdownAnswerText() {
        var evidence = extractor.extract("""
                关键证据：
                - `frontend/web/src/views/ChatView.vue`
                - [QuestionStreamService.java](/repo/backend/src/main/java/cn/welsione/ascoder/question/stream/QuestionStreamService.java:130)
                """);

        assertThat(evidence)
                .extracting("reference")
                .contains(
                        "frontend/web/src/views/ChatView.vue",
                        "/repo/backend/src/main/java/cn/welsione/ascoder/question/stream/QuestionStreamService.java:130"
                );
    }
}
