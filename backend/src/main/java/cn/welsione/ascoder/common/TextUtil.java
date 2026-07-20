package cn.welsione.ascoder.common;

import java.util.regex.Pattern;

/**
 * 字符串相关的小工具方法集合，抽取自分散在各模块的重复逻辑。
 * 仅放纯函数；带 IO 或业务规则的逻辑不属于此类。
 */
public final class TextUtil {

    /** 同时匹配 CSI 序列与残留的 ESC[ 数字; 序列；覆盖 CodeGraph CLI 在不同终端下的两种输出。 */
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*[ -/]*[@-~]|\\x1B\\[[0-9;]*");

    private TextUtil() {
    }

    /**
     * null-safe 的 blank 判断。等价于 {@code value == null || value.isBlank()}，
     * 避免在调用处重复写两段判断。
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** {@link #isBlank(String)} 的反义，便于在判定链上读 trying。 */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    /**
     * 多候选取第一个非空白值；全部为空时返回最后一个候选（保留 null 以便调用方按需 fallback）。
     */
    public static String firstNonBlank(String first, String... rest) {
        if (isNotBlank(first)) {
            return first;
        }
        if (rest == null) {
            return first;
        }
        for (String candidate : rest) {
            if (isNotBlank(candidate)) {
                return candidate;
            }
        }
        return rest.length == 0 ? first : rest[rest.length - 1];
    }

    /**
     * 当值为空白时返回 fallback；否则原样返回，常用于读取可选输入。
     */
    public static String orDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    /**
     * 清除字符串中的 ANSI 转义码。CodeGraph CLI 进度输出会带颜色，写入前端/日志前需要剥离。
     */
    public static String stripAnsi(String text) {
        if (text == null) {
            return null;
        }
        return ANSI.matcher(text).replaceAll("");
    }

    /**
     * 空白字符串返回 null，非空白则 trim。常用于将可选表单输入规范化为 null。
     */
    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
