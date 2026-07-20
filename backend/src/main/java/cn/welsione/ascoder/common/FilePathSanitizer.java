package cn.welsione.ascoder.common;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 文件路径安全校验器，供 Git 工具和 CodeGraph 工具统一调用。
 *
 * <p>LLM 传入的 filePath 可能包含路径遍历（{@code ..}）或绝对路径（{@code /etc/passwd}），
 * 本类在路径进入 git/codegraph 命令前做统一拦截。</p>
 *
 * <p>路径遍历检测采用路径段级别判断：{@code Path.normalize()} 后仍包含 {@code ..} 路径段才拒绝，
 * 允许文件名中包含连续点号（如 {@code data..backup.csv}）。</p>
 *
 * <p>绝对路径（以 {@code /} 开头）自动去掉前导 {@code /}，而非直接拒绝，
 * 因为 LLM 从 CodeGraph 输出复制的路径常为 {@code /src/main/java/Foo.java} 形式。</p>
 */
@Slf4j
public class FilePathSanitizer {

    private static final int MAX_PATH_LENGTH = 500;

    /**
     * 校验路径安全，不安全时返回 {@link Optional#empty()}。
     */
    public Optional<String> sanitizeOptional(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return Optional.empty();
        }
        String trimmed = filePath.trim();

        // 绝对路径自动去掉前导 /
        if (trimmed.startsWith("/")) {
            String original = trimmed;
            trimmed = trimmed.substring(1);
            log.debug("自动去掉前导 /：{} → {}", original, trimmed);
        }

        // 路径遍历检测：Path.normalize() 后仍包含 .. 路径段才拒绝
        if (containsPathTraversal(trimmed) || trimmed.length() > MAX_PATH_LENGTH) {
            log.warn("拒绝不安全文件路径：{}", filePath);
            return Optional.empty();
        }
        try {
            SafePathValidator.sanitizeArg(trimmed);
        } catch (IllegalArgumentException ex) {
            log.warn("拒绝不安全文件路径：{}", filePath);
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }

    /**
     * 校验路径安全，不安全时返回 {@code null}（兼容现有 nullable filePath 语义）。
     */
    public String sanitizeOrNull(String filePath) {
        return sanitizeOptional(filePath).orElse(null);
    }

    /**
     * 路径段级别的遍历检测：{@code Path.normalize()} 后仍包含 {@code ..} 才视为路径遍历。
     * 允许文件名中包含连续点号（如 {@code data..backup.csv}）。
     */
    private boolean containsPathTraversal(String relativePath) {
        String normalized = Path.of(relativePath).normalize().toString();
        return normalized.contains("..");
    }
}
