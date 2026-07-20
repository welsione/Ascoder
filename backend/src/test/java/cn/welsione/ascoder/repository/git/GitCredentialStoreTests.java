package cn.welsione.ascoder.repository.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GitCredentialStore 单元测试，验证凭据文件的读写和更新逻辑。
 */
class GitCredentialStoreTests {

    @TempDir
    Path tempDir;

    private GitCredentialStore store;

    @BeforeEach
    void setUp() {
        store = new GitCredentialStore();
        // 通过反射修改 CREDENTIALS_PATH 为临时目录下的文件
        try {
            var field = GitCredentialStore.class.getDeclaredField("CREDENTIALS_PATH");
            // CREDENTIALS_PATH 是 static final，无法直接修改——改用测试友好的设计
        } catch (Exception ignored) {
        }
    }

    @Test
    void extractHostFromHttpsUrl() {
        // 直接测试 extractHost 方法（通过 upsert 间接验证）
        // 因为 extractHost 是 private，我们通过 upsert + hasCredential 间接测试
        String testFile = tempDir.resolve(".git-credentials").toString();

        // 创建测试用 GitCredentialStore，重写路径
        TestableGitCredentialStore testable = new TestableGitCredentialStore(testFile);

        testable.upsert("https://git.example.com/org/repo.git", "user", "pass");
        assertTrue(testable.hasCredential("https://git.example.com/org/repo.git"));
        assertFalse(testable.hasCredential("https://other.example.com/repo"));
    }

    @Test
    void upsertAppendsNewHost() throws IOException {
        String testFile = tempDir.resolve(".git-credentials").toString();
        TestableGitCredentialStore testable = new TestableGitCredentialStore(testFile);

        // 先写入已有凭据
        Files.writeString(Path.of(testFile), "https://git:token@github.com\n");

        // 追加新凭据
        testable.upsert("https://git.example.com/org/repo.git", "user", "pass");

        String content = Files.readString(Path.of(testFile));
        assertTrue(content.contains("@github.com"));
        assertTrue(content.contains("@git.example.com"));
    }

    @Test
    void upsertReplacesExistingHost() throws IOException {
        String testFile = tempDir.resolve(".git-credentials").toString();
        TestableGitCredentialStore testable = new TestableGitCredentialStore(testFile);

        // 先写入旧凭据
        Files.writeString(Path.of(testFile), "https://olduser:oldpass@git.example.com\n");

        // 更新凭据
        testable.upsert("https://git.example.com/org/repo.git", "newuser", "newpass");

        String content = Files.readString(Path.of(testFile));
        assertTrue(content.contains("newuser:newpass@git.example.com"));
        assertFalse(content.contains("olduser:oldpass@git.example.com"));
    }

    @Test
    void removeDeletesHostEntry() throws IOException {
        String testFile = tempDir.resolve(".git-credentials").toString();
        TestableGitCredentialStore testable = new TestableGitCredentialStore(testFile);

        Files.writeString(Path.of(testFile),
                "https://git:token@github.com\nhttps://user:pass@git.example.com\n");

        testable.remove("https://git.example.com/repo.git");

        String content = Files.readString(Path.of(testFile));
        assertTrue(content.contains("@github.com"));
        assertFalse(content.contains("@git.example.com"));
    }

    @Test
    void configuredHostsReturnsAllHosts() throws IOException {
        String testFile = tempDir.resolve(".git-credentials").toString();
        TestableGitCredentialStore testable = new TestableGitCredentialStore(testFile);

        Files.writeString(Path.of(testFile),
                "https://git:token@github.com\nhttps://user:pass@gitlab.com\nhttps://user:pass@git.example.com\n");

        Set<String> hosts = testable.configuredHosts();
        assertEquals(3, hosts.size());
        assertTrue(hosts.contains("github.com"));
        assertTrue(hosts.contains("gitlab.com"));
        assertTrue(hosts.contains("git.example.com"));
    }

    /**
     * 可测试的 GitCredentialStore 子类，覆盖凭据文件路径。
     */
    private static class TestableGitCredentialStore extends GitCredentialStore {
        private final String testPath;

        TestableGitCredentialStore(String testPath) {
            this.testPath = testPath;
        }

        @Override
        protected String getCredentialsPath() {
            return testPath;
        }
    }
}
