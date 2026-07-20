package cn.welsione.ascoder.loganalysis.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link LogMaskingService} 对常见 PII 与凭证的脱敏行为。
 */
class LogMaskingServiceTests {

    private final LogMaskingService service = new LogMaskingService();

    @Test
    void nullOrEmpty_returnsAsIs() {
        assertThat(service.mask(null)).isNull();
        assertThat(service.mask("")).isEmpty();
    }

    @Test
    void phoneNumber_isMasked() {
        String masked = service.mask("用户手机号 13812345678 注册");
        assertThat(masked).contains("1***********").doesNotContain("13812345678");
    }

    @Test
    void email_isMasked() {
        String masked = service.mask("contact: alice@example.com");
        assertThat(masked).contains("***@***").doesNotContain("alice@example.com");
    }

    @Test
    void idCard_isMasked() {
        String masked = service.mask("身份证 11010519491231002X 校验失败");
        assertThat(masked).doesNotContain("11010519491231002X");
    }

    @Test
    void bearerToken_isMasked() {
        String masked = service.mask("Authorization: Bearer abcdef0123456789ABCDEF");
        assertThat(masked).doesNotContain("abcdef0123456789ABCDEF");
        assertThat(masked).containsIgnoringCase("Bearer ***");
    }

    @Test
    void password_keyValueIsMasked() {
        String masked = service.mask("password=secret123 and apiKey=AKIA1234567890");
        assertThat(masked).doesNotContain("secret123");
        assertThat(masked).doesNotContain("AKIA1234567890");
    }

    @Test
    void longHexToken_isMasked() {
        String hex = "deadbeef".repeat(8);
        String masked = service.mask("token=" + hex);
        assertThat(masked).doesNotContain(hex);
    }

    @Test
    void cookieHeader_isMasked() {
        String masked = service.mask("Cookie: SESSIONID=abcdefg123456");
        assertThat(masked).doesNotContain("abcdefg123456");
    }
}
