package cn.welsione.ascoder.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafePathValidatorTest {

    @Test
    void resolvesRelativePath(@TempDir Path tempDir) throws Exception {
        // 创建实际目录，使 toRealPath 能解析符号链接
        Files.createDirectories(tempDir.resolve("src/main"));
        Path resolved = SafePathValidator.validateUnderRoot(tempDir, "src/main");
        assertThat(resolved).isEqualTo(tempDir.resolve("src/main").normalize().toAbsolutePath());
    }

    @Test
    void resolvesNonExistentPath(@TempDir Path tempDir) {
        // 不存在的路径退回 normalize 校验，仍可解析
        Path resolved = SafePathValidator.validateUnderRoot(tempDir, "src/main");
        assertThat(resolved).isEqualTo(tempDir.resolve("src/main").normalize().toAbsolutePath());
    }

    @Test
    void rejectsEmptyPath() {
        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(Path.of("/tmp"), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(Path.of("/tmp"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(Path.of("/tmp"), "../etc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("..");
        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(Path.of("/tmp"), "a/../../b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsShellMetacharacters() {
        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(Path.of("/tmp"), "file;rm"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(Path.of("/tmp"), "a|b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(Path.of("/tmp"), "a&b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(Path.of("/tmp"), "a$b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(Path.of("/tmp"), "a`b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(Path.of("/tmp"), "a>b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 符号链接穿越：在 root 下创建指向 root 外的符号链接，校验必须拒绝。
     * 仅在支持符号链接的环境（Linux/macOS）执行。
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void rejectsSymlinkEscape(@TempDir Path tempDir) throws Exception {
        // root 外的目标文件
        Path outsideTarget = tempDir.resolve("../outside-secret.txt").normalize();
        Files.writeString(outsideTarget, "secret");

        // root 内的符号链接指向 root 外
        Path link = tempDir.resolve("escape-link");
        try {
            Files.createSymbolicLink(link, outsideTarget.toAbsolutePath());
        } catch (Exception ex) {
            // 无权限创建符号链接时跳过
            return;
        }

        assertThatThrownBy(() -> SafePathValidator.validateUnderRoot(tempDir, "escape-link"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of bounds");
    }

    @Test
    void sanitizeArgAcceptsSafeArg() {
        SafePathValidator.sanitizeArg("--name=value");
        SafePathValidator.sanitizeArg("src/main/java");
        SafePathValidator.sanitizeArg("foo.txt");
    }

    @Test
    void sanitizeArgRejectsNullAndDangerous() {
        SafePathValidator.sanitizeArg(null);  // null 视为安全

        assertThatThrownBy(() -> SafePathValidator.sanitizeArg("a;b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafePathValidator.sanitizeArg("a|b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafePathValidator.sanitizeArg("a`b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafePathValidator.sanitizeArg("a\nb"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
