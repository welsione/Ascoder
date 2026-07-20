package cn.welsione.ascoder.loganalysis.application;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 日志敏感信息脱敏服务，覆盖手机号、邮箱、身份证、token、cookie 等常见 PII。
 * 仅做基础正则替换，不依赖外部规则库；Service 层调用前必须通过该方法处理日志原文。
 */
@Service
public class LogMaskingService {

    private static final Pattern PHONE = Pattern.compile("(?<![\\d-])1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ID_CARD = Pattern.compile("(?<![\\dX])\\d{17}[\\dXx](?![\\dXx])");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)([A-Za-z0-9._\\-+/=]{16,})");
    private static final Pattern AUTH_HEADER = Pattern.compile("(?i)(authorization\\s*[:=]\\s*)([^\\s,;]{8,})");
    private static final Pattern COOKIE = Pattern.compile("(?i)(cookie\\s*[:=]\\s*)([^\\s]+)");
    private static final Pattern PASSWORD = Pattern.compile("(?i)(password|pwd|passwd|secret|apiKey|api_key|access_token|refresh_token)\\s*[:=]\\s*\"?([^\\s\",;]+)");
    private static final Pattern LONG_HEX_TOKEN = Pattern.compile("\\b[a-fA-F0-9]{32,}\\b");

    public String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = text;
        masked = PHONE.matcher(masked).replaceAll("1***********");
        masked = EMAIL.matcher(masked).replaceAll("***@***");
        masked = ID_CARD.matcher(masked).replaceAll("******************");
        masked = BEARER.matcher(masked).replaceAll("$1***");
        masked = AUTH_HEADER.matcher(masked).replaceAll("$1***");
        masked = COOKIE.matcher(masked).replaceAll("$1***");
        masked = PASSWORD.matcher(masked).replaceAll("$1=***");
        masked = LONG_HEX_TOKEN.matcher(masked).replaceAll("***");
        return masked;
    }
}
