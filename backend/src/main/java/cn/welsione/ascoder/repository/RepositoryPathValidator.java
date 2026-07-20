package cn.welsione.ascoder.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * 仓库路径校验器，确保仓库路径在允许的根目录下且真实存在。
 */
@Slf4j
@Component
public class RepositoryPathValidator {

    private final Path repoRoot;

    public RepositoryPathValidator(@Value("${ascoder.repo-root}") String repoRoot) {
        this.repoRoot = normalizeRepoRoot(repoRoot);
    }

    public Path normalizeRepoRoot(String repoRoot) {
        Path normalized = Path.of(repoRoot).toAbsolutePath().normalize();
        try {
            normalized = normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException ex) {
            log.warn("仓库根目录无法解析为真实路径：{}，使用规范化路径", repoRoot);
        }
        return normalized;
    }

    public Path validateUnderRoot(Path root, String relativeOrAbsolute) {
        Path resolved = root.resolve(relativeOrAbsolute).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            log.warn("路径越界检测：{} 不在根目录 {} 下", resolved, root);
            throw new InvalidRepositoryPathException("路径必须在仓库根目录下");
        }
        if (!resolved.toFile().exists()) {
            throw new InvalidRepositoryPathException("路径不存在：" + resolved);
        }
        return resolved;
    }

    public Path resolveCreatableUnderRoot(Path root, String relativeOrAbsolute) {
        Path resolved = root.resolve(relativeOrAbsolute).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            log.warn("路径越界检测：{} 不在根目录 {} 下", resolved, root);
            throw new InvalidRepositoryPathException("路径必须在仓库根目录下");
        }
        if (Files.exists(resolved)) {
            if (!Files.isDirectory(resolved)) {
                throw new InvalidRepositoryPathException("目标路径已存在且不是目录：" + resolved);
            }
            String[] children = resolved.toFile().list();
            if (children != null && children.length > 0) {
                throw new InvalidRepositoryPathException("目标路径已存在且不为空：" + resolved);
            }
        }
        return resolved;
    }
}
