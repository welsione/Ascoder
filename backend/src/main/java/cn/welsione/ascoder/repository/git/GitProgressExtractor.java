package cn.welsione.ascoder.repository.git;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 git 命令的进度输出行，提取百分比。
 *
 * <p>git clone/fetch/pull 在带 {@code --progress} 参数时会输出形如
 * {@code "Receiving objects:  45% (560/1234)"} 的进度行。
 * 本类从这些行中提取百分比，供 TaskDefinition 映射到任务整体进度。</p>
 */
public final class GitProgressExtractor {

    /** 匹配 git 各阶段的进度行，如 "Receiving objects:  45%" 或 "remote: Compressing objects: 100%"。 */
    private static final Pattern PROGRESS_PATTERN = Pattern.compile(
            "(?:Receiving objects|Unpacking objects|Resolving deltas|Counting objects|Compressing objects)[^:]*:\\s*(\\d+)%");

    private GitProgressExtractor() {
    }

    /**
     * 从 git 输出行中提取进度百分比。
     *
     * @param line git 输出行
     * @return 0-100 的百分比，无法解析时返回 -1
     */
    public static int extractPercent(String line) {
        if (line == null || line.isBlank()) {
            return -1;
        }
        Matcher matcher = PROGRESS_PATTERN.matcher(line);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
