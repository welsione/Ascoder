package cn.welsione.ascoder.codegraph.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import cn.welsione.ascoder.common.CommandResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class CodeGraphCommandRunnerTests {

    private final CodeGraphCommandRunner runner = new CodeGraphCommandRunner(Map.of());

    @TempDir
    Path tempDir;

    @Test
    void capturesCommandOutput() {
        CommandResult result = runner.run(List.of("sh", "-c", "printf ok"), tempDir, Duration.ofSeconds(5));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEqualTo("ok");
    }
}
