package cn.welsione.ascoder.common;

import cn.welsione.ascoder.common.exception.ValidationException;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * 文件系统工具：清理目录、路径归一化、托管目录守卫。
 * 集中通用文件操作，避免散落在各业务 Service 中重复实现。
 */
public final class FileUtil {

    private static final String SAFE_PATH_REPLACEMENT_PATTERN = "[^a-zA-Z0-9._-]";

    private FileUtil() {
    }

    /**
     * 将任意字符串归一化为安全的文件名片段，非字母数字和 ._- 字符全部替换为下划线。
     */
    public static String safePathPart(String value) {
        return value == null || value.isBlank()
                ? "unnamed"
                : value.trim().replaceAll(SAFE_PATH_REPLACEMENT_PATTERN, "_");
    }

    /**
     * 递归删除目录及其内容；目录不存在时静默返回。
     * 任意子节点删除失败立即抛出，便于事务回滚。
     */
    public static void deleteDirectoryIfExists(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (Exception ex) {
                            throw new IllegalStateException("删除目录失败：" + current, ex);
                        }
                    });
        } catch (Exception ex) {
            throw new ValidationException(ex.getMessage(), ex);
        }
    }

    /**
     * 确保 path 处于 root 之下，否则抛出 {@link ValidationException}。
     * 用于校验用户传入路径是否越界到托管目录之外。
     */
    public static void ensureUnderRoot(Path path, Path root, String label) {
        if (!path.startsWith(root)) {
            throw new ValidationException(label + " 不在托管目录下");
        }
    }
}
