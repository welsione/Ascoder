package cn.welsione.ascoder.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SafeCommandRunnerTest {

    private final SafeCommandRunner runner = new SafeCommandRunner();

    @Test
    void runsSimpleCommand(@TempDir Path tempDir) {
        CommandResult result = runner.run(
                List.of("echo", "hello world"),
                tempDir,
                Duration.ofSeconds(5),
                Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("hello world");
    }

    @Test
    void capturesNonZeroExitCode(@TempDir Path tempDir) {
        // sh -c 'exit 42' 不是命令，跨平台兼容用 false（Unix/macOS）/ true 测试
        CommandResult result = runner.run(
                List.of("sh", "-c", "exit 42"),
                tempDir,
                Duration.ofSeconds(5),
                Map.of());

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void destroysProcessOnTimeout(@TempDir Path tempDir) {
        // sleep 10 秒，超时 1 秒
        long start = System.currentTimeMillis();
        CommandResult result = runner.run(
                List.of("sh", "-c", "sleep 10"),
                tempDir,
                Duration.ofMillis(500),
                Map.of());
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.isSuccess()).isFalse();
        assertThat(elapsed).isLessThan(3000L);
    }

    @Test
    void runsCommandInSpecifiedWorkingDirectory(@TempDir Path tempDir) throws Exception {
        CommandResult result = runner.run(
                List.of("pwd"),
                tempDir,
                Duration.ofSeconds(5),
                Map.of());

        assertThat(result.isSuccess()).isTrue();
        // macOS may resolve /var/... to /private/var/... (symlink); 检查真实路径
        String actual = result.getOutput().trim();
        Path expectedReal = tempDir.toRealPath();
        assertThat(actual).isEqualTo(expectedReal.toString());
    }

    @Test
    void returnsFailureForNonExistentCommand(@TempDir Path tempDir) {
        CommandResult result = runner.run(
                List.of("nonexistent_command_xyz_12345"),
                tempDir,
                Duration.ofSeconds(2),
                Map.of());

        assertThat(result.isSuccess()).isFalse();
    }
}
