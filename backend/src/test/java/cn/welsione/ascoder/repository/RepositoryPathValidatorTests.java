package cn.welsione.ascoder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryPathValidatorTests {

    @TempDir
    Path tempDir;

    @Test
    void validatesRelativePathUnderRoot() throws Exception {
        Path repo = Files.createDirectory(tempDir.resolve("demo"));
        RepositoryPathValidator validator = new RepositoryPathValidator(tempDir.toString());

        Path normalized = validator.validateUnderRoot(tempDir, "demo");

        assertThat(normalized).isEqualTo(repo);
    }

    @Test
    void rejectsPathOutsideRoot() throws Exception {
        Path outside = Files.createTempDirectory(tempDir.getParent(), "outside-repo-");
        RepositoryPathValidator validator = new RepositoryPathValidator(tempDir.toString());

        assertThatThrownBy(() -> validator.validateUnderRoot(tempDir, outside.toString()))
                .isInstanceOf(InvalidRepositoryPathException.class)
                .hasMessageContaining("仓库根目录");
    }
}
