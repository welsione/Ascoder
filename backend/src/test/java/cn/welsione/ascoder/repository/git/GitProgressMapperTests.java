package cn.welsione.ascoder.repository.git;

import cn.welsione.ascoder.common.task.TaskProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * GitProgressMapper 单元测试，覆盖分阶段进度映射和单调递增约束。
 */
class GitProgressMapperTests {

    private TaskProgress progress;
    private GitProgressMapper mapper;

    @BeforeEach
    void setUp() {
        progress = mock(TaskProgress.class);
        mapper = new GitProgressMapper(progress, 0, 80);
    }

    @Test
    void countingObjectsMapsToFirst5Percent() {
        mapper.onLine("Counting objects:  50%");

        // stageStart=0.0, stageEnd=0.05, 50% -> stageProgress=0.025
        // taskPercent = 0 + (int)(0.025 * 80) = 2
        verify(progress).update(2, "Counting objects:  50%");
    }

    @Test
    void compressingObjectsMapsTo5To10Percent() {
        mapper.onLine("Compressing objects:  50%");

        // stageStart=0.05, stageEnd=0.10, 50% -> stageProgress=0.075
        // taskPercent = 0 + (int)(0.075 * 80) = 6
        verify(progress).update(6, "Compressing objects:  50%");
    }

    @Test
    void receivingObjectsMapsTo10To85Percent() {
        mapper.onLine("Receiving objects:  50%");

        // stageStart=0.10, stageEnd=0.85, 50% -> stageProgress=0.475
        // taskPercent = 0 + (int)(0.475 * 80) = 38
        verify(progress).update(38, "Receiving objects:  50%");
    }

    @Test
    void resolvingDeltasMapsTo85To95Percent() {
        mapper.onLine("Resolving deltas:  50%");

        // stageStart=0.85, stageEnd=0.95, 50% -> stageProgress=0.90
        // taskPercent = 0 + (int)(0.90 * 80) = 72
        verify(progress).update(72, "Resolving deltas:  50%");
    }

    @Test
    void nonProgressLineDoesNotCallUpdate() {
        mapper.onLine("Already up to date.");

        verify(progress, never()).update(anyInt(), anyString());
    }

    @Test
    void nullLineDoesNotCallUpdate() {
        mapper.onLine(null);

        verify(progress, never()).update(anyInt(), anyString());
    }

    @Test
    void progressMonotonicallyIncreases() {
        mapper.onLine("Receiving objects:  30%");
        mapper.onLine("Receiving objects:  60%");
        mapper.onLine("Receiving objects:  30%"); // 重复低值，不应更新

        // 第一次: 0.10 + 0.75 * 0.30 = 0.325 -> (int)(0.325 * 80) = 26
        verify(progress).update(26, "Receiving objects:  30%");
        // 第二次: 0.10 + 0.75 * 0.60 = 0.55 -> (int)(0.55 * 80) = 44
        verify(progress).update(44, "Receiving objects:  60%");
        // 第三次: 26，不大于 lastPercent=44，不调用
        verify(progress, times(2)).update(anyInt(), anyString());
    }

    @Test
    void basePercentAndRangeApplied() {
        GitProgressMapper offsetMapper = new GitProgressMapper(progress, 80, 20);
        offsetMapper.onLine("Receiving objects:  50%");

        // stageProgress=0.475, taskPercent = 80 + (int)(0.475 * 20) = 80 + 9 = 89
        verify(progress).update(89, "Receiving objects:  50%");
    }

    @Test
    void longMessageTruncated() {
        String longLine = "Receiving objects:  10% " + "x".repeat(150);
        mapper.onLine(longLine);

        verify(progress).update(anyInt(), argThat(msg -> msg.length() <= 103)); // 100 + "..."
    }
}
