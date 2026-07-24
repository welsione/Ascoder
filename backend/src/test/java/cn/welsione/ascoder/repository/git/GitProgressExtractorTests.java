package cn.welsione.ascoder.repository.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitProgressExtractor 单元测试，覆盖 git 进度行百分比提取。
 */
class GitProgressExtractorTests {

    @Test
    void extractReceivingObjects() {
        assertEquals(45, GitProgressExtractor.extractPercent("Receiving objects:  45% (560/1234)"));
    }

    @Test
    void extractResolvingDeltas() {
        assertEquals(23, GitProgressExtractor.extractPercent("Resolving deltas:  23%"));
    }

    @Test
    void extractUnpackingObjects() {
        assertEquals(78, GitProgressExtractor.extractPercent("Unpacking objects:  78% (100/128)"));
    }

    @Test
    void extractCountingObjects() {
        assertEquals(100, GitProgressExtractor.extractPercent("Counting objects: 100% (1234)"));
    }

    @Test
    void extractCompressingObjects() {
        assertEquals(50, GitProgressExtractor.extractPercent("Compressing objects:  50%"));
    }

    @Test
    void extractRemotePrefix() {
        assertEquals(30, GitProgressExtractor.extractPercent("remote: Compressing objects:  30%"));
    }

    @Test
    void noMatchReturnsMinusOne() {
        assertEquals(-1, GitProgressExtractor.extractPercent("Already up to date."));
    }

    @Test
    void nullReturnsMinusOne() {
        assertEquals(-1, GitProgressExtractor.extractPercent(null));
    }

    @Test
    void blankReturnsMinusOne() {
        assertEquals(-1, GitProgressExtractor.extractPercent(""));
    }

    @Test
    void nonGitProgressLine() {
        assertEquals(-1, GitProgressExtractor.extractPercent("From https://github.com/org/repo"));
    }
}
