package cn.welsione.ascoder.agent.infrastructure.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerStyleTests {

    @Test
    void fromRole_developer() {
        assertThat(AnswerStyle.fromRole("developer")).isEqualTo(AnswerStyle.DEVELOPER);
    }

    @Test
    void fromRole_productManager() {
        assertThat(AnswerStyle.fromRole("product_manager")).isEqualTo(AnswerStyle.PRODUCT_MANAGER);
    }

    @Test
    void fromRole_tester() {
        assertThat(AnswerStyle.fromRole("tester")).isEqualTo(AnswerStyle.TESTER);
    }

    @Test
    void fromRole_null_defaultsToDeveloper() {
        assertThat(AnswerStyle.fromRole(null)).isEqualTo(AnswerStyle.DEVELOPER);
    }

    @Test
    void fromRole_blank_defaultsToDeveloper() {
        assertThat(AnswerStyle.fromRole("  ")).isEqualTo(AnswerStyle.DEVELOPER);
    }

    @Test
    void fromRole_unknown_defaultsToDeveloper() {
        assertThat(AnswerStyle.fromRole("architect")).isEqualTo(AnswerStyle.DEVELOPER);
    }

    @Test
    void fromRole_hyphenAndSpaceNormalized() {
        assertThat(AnswerStyle.fromRole("product-manager")).isEqualTo(AnswerStyle.PRODUCT_MANAGER);
        assertThat(AnswerStyle.fromRole("product manager")).isEqualTo(AnswerStyle.PRODUCT_MANAGER);
    }

    @Test
    void instruction_isNotBlank() {
        for (AnswerStyle style : AnswerStyle.values()) {
            assertThat(style.getInstruction()).isNotBlank();
        }
    }

    @Test
    void instruction_loadedFromTemplate_containsRequiredSections() {
        for (AnswerStyle style : AnswerStyle.values()) {
            assertThat(style.getInstruction()).contains("回答要求");
        }
    }

    @Test
    void developerInstruction_allowsCode() {
        assertThat(AnswerStyle.DEVELOPER.getInstruction()).contains("完整展示关键代码片段");
    }

    @Test
    void productManagerInstruction_forbidsCode() {
        assertThat(AnswerStyle.PRODUCT_MANAGER.getInstruction()).contains("禁止展示代码片段");
    }

    @Test
    void testerInstruction_forbidsCode() {
        assertThat(AnswerStyle.TESTER.getInstruction()).contains("禁止展示代码片段");
    }
}
