package cn.welsione.ascoder.loganalysis.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link LogPreprocessService} 的级别统计、异常聚合与 traceId 抽取等核心行为。
 */
class LogPreprocessServiceTests {

    private LogPreprocessService createService() {
        LogPreprocessService service = new LogPreprocessService();
        ReflectionTestUtils.setField(service, "maxScanBytes", 52_428_800L);
        ReflectionTestUtils.setField(service, "maxKeywordHits", 200);
        ReflectionTestUtils.setField(service, "maxTraceIds", 50);
        return service;
    }

    @Test
    void preprocess_aggregatesLevelsAndExceptions(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("app.log");
        String content = String.join("\n",
                "2026-06-12 09:00:00 INFO traceId=req-001 GET /users",
                "2026-06-12 09:00:01 ERROR traceId=req-001 NullPointerException: value is null",
                "    at com.example.UserService.findUser(UserService.java:42)",
                "    at com.example.UserController.handle(UserController.java:21)",
                "2026-06-12 09:00:02 WARN traceId=req-002 slow query",
                "2026-06-12 09:00:03 ERROR traceId=req-003 NullPointerException: value is null",
                "    at com.example.UserService.findUser(UserService.java:42)",
                ""
        );
        Files.writeString(file, content, StandardCharsets.UTF_8);

        LogFileSummary summary = createService().preprocess(7L, "app.log", file);

        assertThat(summary.getFileId()).isEqualTo(7L);
        assertThat(summary.getDisplayName()).isEqualTo("app.log");
        assertThat(summary.getErrorCount()).isEqualTo(2);
        assertThat(summary.getWarnCount()).isEqualTo(1);
        assertThat(summary.getInfoCount()).isEqualTo(1);
        assertThat(summary.getTraceIds()).contains("req-001", "req-002", "req-003");
        assertThat(summary.getExceptionGroups()).hasSize(1);
        LogFileSummary.ExceptionGroup group = summary.getExceptionGroups().get(0);
        assertThat(group.getExceptionClass()).contains("NullPointerException");
        assertThat(group.getCount()).isEqualTo(2);
        assertThat(group.getTopApplicationFrames()).isNotEmpty();
    }

    @Test
    void preprocess_emptyFile_yieldsZeroCounts(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("empty.log");
        Files.writeString(file, "", StandardCharsets.UTF_8);

        LogFileSummary summary = createService().preprocess(1L, "empty.log", file);

        assertThat(summary.getLineCount()).isZero();
        assertThat(summary.getErrorCount()).isZero();
        assertThat(summary.getExceptionGroups()).isEmpty();
        assertThat(summary.isLimitedMode()).isFalse();
    }

    @Test
    void preprocess_enforcesScanLimit_marksLimitedMode(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("big.log");
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            big.append("2026-06-12 09:00:00 INFO line-").append(i).append('\n');
        }
        Files.writeString(file, big.toString(), StandardCharsets.UTF_8);

        LogPreprocessService service = createService();
        ReflectionTestUtils.setField(service, "maxScanBytes", 100L);

        LogFileSummary summary = service.preprocess(2L, "big.log", file);

        assertThat(summary.isLimitedMode()).isTrue();
        assertThat(summary.getLineCount()).isPositive();
    }
}
