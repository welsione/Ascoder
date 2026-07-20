package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentRuntimeHelper 静态工具方法测试。
 */
class AgentRuntimeHelperTests {

    @Test
    void specialistWorkspaceReturnsPathEndingWithAscoderSpecialist() {
        AgentRequest request = request();

        Path result = AgentRuntimeHelper.specialistWorkspace(request);

        assertTrue(result.endsWith(".ascoder-specialist"),
                "specialistWorkspace 应返回以 .ascoder-specialist 结尾的路径，实际: " + result);
    }

    @Test
    void specialistWorkspaceDiffersFromHarnessWorkspace() {
        AgentRequest request = request();

        Path specialist = AgentRuntimeHelper.specialistWorkspace(request);
        Path harness = AgentRuntimeHelper.harnessWorkspace(request);

        assertNotEquals(specialist, harness,
                "specialistWorkspace 和 harnessWorkspace 应返回不同路径");
    }

    @Test
    void specialistWorkspaceResolvesUnderProjectSpaceRoot() {
        AgentRequest request = request();

        Path result = AgentRuntimeHelper.specialistWorkspace(request);

        assertTrue(result.startsWith(request.getProjectSpaceRootPath()),
                "specialistWorkspace 应在项目空间根目录下，实际: " + result);
    }

    private AgentRequest request() {
        return new AgentRequest(
                1L, null, 100L, "demo-space", "/tmp/demo-space", "/tmp/demo-space/.codegraph",
                List.of(), "问题", "developer", null, null, null, null, null);
    }
}
