package cn.welsione.ascoder.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GitSyncOperation} 单元测试，覆盖 code 往返、null 兜底和非法值抛异常。
 */
class GitSyncOperationTests {

    @Test
    void fetchCodeIsFetch() {
        assertEquals("fetch", GitSyncOperation.FETCH.code());
    }

    @Test
    void pullCodeIsPull() {
        assertEquals("pull", GitSyncOperation.PULL.code());
    }

    @Test
    void fromCodeResolvesFetch() {
        assertSame(GitSyncOperation.FETCH, GitSyncOperation.fromCode("fetch"));
    }

    @Test
    void fromCodeResolvesPull() {
        assertSame(GitSyncOperation.PULL, GitSyncOperation.fromCode("pull"));
    }

    @Test
    void fromCodeNullDefaultsToFetch() {
        assertSame(GitSyncOperation.FETCH, GitSyncOperation.fromCode(null));
    }

    @Test
    void fromCodeInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> GitSyncOperation.fromCode("clone"));
    }

    @Test
    void codeRoundTrip() {
        for (GitSyncOperation operation : GitSyncOperation.values()) {
            assertSame(operation, GitSyncOperation.fromCode(operation.code()));
        }
    }
}
