package cn.welsione.ascoder.agent.extension.config;

import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.ascoder.agent.application.AgentRuntimeRegistry;
import cn.welsione.ascoder.agent.application.AgentRuntimeView;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.domain.AgentRuntimeStatus;
import cn.welsione.ascoder.agent.domain.SpecialistTaskKind;
import cn.welsione.ascoder.common.exception.GlobalExceptionHandler;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AgentConfigController REST 端点 MockMvc 测试。
 */
@WebMvcTest(AgentConfigController.class)
@Import(GlobalExceptionHandler.class)
class AgentConfigControllerTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AgentConfigService service;
    @MockBean private AgentRuntimeRegistry runtimeRegistry;

    @Test
    void listReturnsConfigs() throws Exception {
        AgentConfig config = agentConfig("code-researcher", AgentRole.SPECIALIST);
        when(service.list()).thenReturn(List.of(config));
        when(runtimeRegistry.snapshot()).thenReturn(List.of());

        mvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agentId").value("code-researcher"))
                .andExpect(jsonPath("$[0].runtimeStatus").value("IDLE"));
    }

    @Test
    void createReturnsCreated() throws Exception {
        AgentConfig config = agentConfig("new-agent", AgentRole.SPECIALIST);
        when(service.create(any())).thenReturn(config);

        CreateAgentConfigRequest request = new CreateAgentConfigRequest();
        request.setAgentId("new-agent");
        request.setDisplayName("New Agent");
        request.setAgentRole(AgentRole.SPECIALIST);
        request.setTaskKind(SpecialistTaskKind.CODE_RESEARCH);
        request.setSystemPrompt("sys");
        request.setTaskTemplate("{{question}}");
        request.setMaxIters(10);

        mvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agentId").value("new-agent"));
    }

    @Test
    void getMissingReturns404() throws Exception {
        when(service.get(99L)).thenThrow(new ResourceNotFoundException("Agent", 99));

        mvc.perform(get("/api/agents/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBuiltinReturns409() throws Exception {
        doThrow(new InvalidStateException("内置 Agent 禁止删除")).when(service).delete(1L);

        mvc.perform(delete("/api/agents/1"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateEnabledReturnsConfig() throws Exception {
        AgentConfig config = agentConfig("code-researcher", AgentRole.SPECIALIST);
        config.setEnabled(false);
        when(service.updateEnabled(1L, false)).thenReturn(config);

        mvc.perform(patch("/api/agents/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void testRenderReturnsRenderedText() throws Exception {
        TestRenderResponse response = new TestRenderResponse("渲染结果", List.of());
        when(service.testRender(anyLong(), any())).thenReturn(response);

        TestRenderRequest request = new TestRenderRequest();
        mvc.perform(post("/api/agents/1/test-render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renderedText").value("渲染结果"));
    }

    @Test
    void statusReturnsSnapshot() throws Exception {
        AgentRuntimeView view = new AgentRuntimeView("code-researcher", AgentRuntimeStatus.RUNNING, 1L, 10L, LocalDateTime.now());
        when(runtimeRegistry.snapshot()).thenReturn(List.of(view));

        mvc.perform(get("/api/agents/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agentId").value("code-researcher"))
                .andExpect(jsonPath("$[0].status").value("RUNNING"));
    }

    private AgentConfig agentConfig(String agentId, AgentRole role) {
        AgentConfig config = new AgentConfig();
        config.setId(1L);
        config.setAgentId(agentId);
        config.setDisplayName(agentId);
        config.setAgentRole(role);
        config.setSystemPrompt("sys");
        config.setTaskTemplate("{{question}}");
        config.setMaxIters(10);
        config.setEnabled(true);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return config;
    }
}
