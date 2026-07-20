package cn.welsione.ascoder.common;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * 集中式路径安全校验。供文件检查 / 文本搜索 / 项目元数据 / 受限命令工具共用。
 * <p>
 * 防护范围：
 * <ul>
 *   <li>路径遍历：拒绝含 {@code ..} 的相对路径</li>
 *   <li>shell 注入：拒绝含 {@code ; & | $ > < \n \r \ ' "} 的参数</li>
 *   <li>越界：解析后的绝对路径必须仍在根目录下</li>
 *   <li>符号链接穿越：使用 {@code toRealPath()} 解析符号链接后再校验</li>
 * </ul>
 */
public final class SafePathValidator {

    private static final Pattern FORBIDDEN = Pattern.compile("[;&|`$><\\n\\r\\\\'\"]");

    private SafePathValidator() {
    }

    /**
     * 解析并验证 path 必须在 root 之下。返回绝对路径（normalize 后，未解析符号链接）。
     * <p>
     * 越界校验使用 {@code toRealPath()} 解析符号链接：若路径已存在，则按真实物理路径校验，
     * 防止攻击者在 root 下创建指向 root 外的符号链接来绕过校验（TOCTOU 竞态条件）；
     * 若路径尚不存在，则退回 {@code normalize()} 校验，由调用方在后续访问时处理不存在情况。
     *
     * @throws IllegalArgumentException 路径越界、为空或包含非法字符
     */
    public static Path validateUnderRoot(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Path must not be empty");
        }
        if (relativePath.contains("..")) {
            throw new IllegalArgumentException("Path must not contain '..'");
        }
        if (FORBIDDEN.matcher(relativePath).find()) {
            throw new IllegalArgumentException("Path contains forbidden characters: " + relativePath);
        }

        Path rootAbs = root.toAbsolutePath().normalize();
        Path resolved = rootAbs.resolve(relativePath).normalize();

        // 对存在的路径用 toRealPath 解析符号链接，消除 TOCTOU 竞态；不存在的路径退回 normalize
        Path rootReal = realPathOrNormalized(rootAbs);
        Path resolvedReal = realPathOrNormalized(resolved);

        if (!resolvedReal.startsWith(rootReal)) {
            throw new IllegalArgumentException("Path out of bounds: " + relativePath);
        }
        return resolved;
    }

    /**
     * 路径存在时返回 toRealPath（解析符号链接）；不存在时解析最长存在祖先的 toRealPath
     * 再拼回剩余部分，确保与 root 的 realPath 在同一坐标系下比较。
     */
    private static Path realPathOrNormalized(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (java.nio.file.Files.exists(absolute)) {
            try {
                return absolute.toRealPath();
            } catch (IOException ex) {
                throw new IllegalStateException("Path cannot be resolved: " + absolute, ex);
            }
        }
        // 路径不存在：找到最长存在的祖先，解析其 realPath 后拼回剩余部分
        Path ancestor = absolute;
        while (ancestor != null && !java.nio.file.Files.exists(ancestor)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) {
            return absolute;
        }
        try {
            return ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize();
        } catch (IOException ex) {
            return absolute;
        }
    }

    /**
     * 检查 shell 参数是否安全。含非法字符抛异常。
     */
    public static void sanitizeArg(String arg) {
        if (arg == null) {
            return;
        }
        if (FORBIDDEN.matcher(arg).find()) {
            throw new IllegalArgumentException("Argument contains forbidden characters: " + arg);
        }
    }
}
