package cn.welsione.ascoder.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link TextUtil} 各纯函数在 null/空白/正常输入下的行为。
 */
class TextUtilTests {

    @Test
    void isBlank_handlesNullAndWhitespace() {
        assertThat(TextUtil.isBlank(null)).isTrue();
        assertThat(TextUtil.isBlank("")).isTrue();
        assertThat(TextUtil.isBlank("   ")).isTrue();
        assertThat(TextUtil.isBlank("a")).isFalse();
        assertThat(TextUtil.isNotBlank("a")).isTrue();
    }

    @Test
    void orDefault_returnsFallbackWhenBlank() {
        assertThat(TextUtil.orDefault(null, "fb")).isEqualTo("fb");
        assertThat(TextUtil.orDefault("  ", "fb")).isEqualTo("fb");
        assertThat(TextUtil.orDefault("v", "fb")).isEqualTo("v");
    }

    @Test
    void firstNonBlank_picksFirstAvailable() {
        assertThat(TextUtil.firstNonBlank("a", "b")).isEqualTo("a");
        assertThat(TextUtil.firstNonBlank(null, "b")).isEqualTo("b");
        assertThat(TextUtil.firstNonBlank("", "  ", "c")).isEqualTo("c");
        assertThat(TextUtil.firstNonBlank("", "", "")).isEqualTo("");
    }

    @Test
    void stripAnsi_removesCsiAndOrphanEscapeSequences() {
        String input = "[31mred[0m plain [0;1mformatted";
        String output = TextUtil.stripAnsi(input);
        assertThat(output).isEqualTo("red plain formatted");
    }

    @Test
    void stripAnsi_handlesNull() {
        assertThat(TextUtil.stripAnsi(null)).isNull();
    }
}
