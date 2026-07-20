package cn.welsione.ascoder.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PathMatcherGlobTest {

    @TempDir
    Path tempDir;

    @Test
    void globStarStarMatchesJavaFiles() throws IOException {
        // 创建目录结构
        Path srcMain = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcMain);
        Files.writeString(srcMain.resolve("App.java"), "class App {}");
        Files.writeString(srcMain.resolve("Service.java"), "class Service {}");

        // 创建非 Java 文件
        Files.writeString(tempDir.resolve("README.md"), "# readme");

        // 测试 glob:**/*.java
        String pattern = "glob:**/*.java";
        PathMatcher matcher = tempDir.getFileSystem().getPathMatcher(pattern);

        try (Stream<Path> walk = Files.walk(tempDir, 20)) {
            List<Path> matched = walk
                    .filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .collect(Collectors.toList());

            System.out.println("Pattern: " + pattern);
            System.out.println("Root: " + tempDir);
            for (Path p : matched) {
                System.out.println("  matched: " + tempDir.relativize(p));
            }
            System.out.println("Matched count: " + matched.size());

            assertEquals(2, matched.size(), "Should match 2 .java files");
        }
    }

    @Test
    void globWithBaseDirMatchesJavaFiles() throws IOException {
        Path srcMain = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcMain);
        Files.writeString(srcMain.resolve("App.java"), "class App {}");

        // 模拟 file_glob 的 base="." 模式
        String pattern = "glob:**/*.java";
        PathMatcher matcher = tempDir.getFileSystem().getPathMatcher(pattern);

        try (Stream<Path> walk = Files.walk(tempDir, 20)) {
            List<String> results = walk
                    .filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .limit(50)
                    .map(p -> tempDir.relativize(p).toString())
                    .collect(Collectors.toList());

            System.out.println("Results: " + results);
            assertFalse(results.isEmpty(), "Should find at least one .java file");
        }
    }
}
