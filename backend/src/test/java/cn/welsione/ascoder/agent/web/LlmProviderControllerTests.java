package cn.welsione.ascoder.agent.web;

import cn.welsione.ascoder.agent.application.LlmProviderService;
import cn.welsione.ascoder.agent.domain.ConnectionTestResult;
import cn.welsione.ascoder.agent.domain.LlmProvider;
import cn.welsione.ascoder.agent.domain.LlmProviderType;
import cn.welsione.ascoder.agent.extension.config.CreateLlmProviderRequest;
import cn.welsione.ascoder.agent.extension.config.UpdateLlmProviderRequest;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LlmProviderController REST 端点 MockMvc 测试。
 */
@WebMvcTest(LlmProviderController.class)
@Import(GlobalExceptionHandler.class)
class LlmProviderControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LlmProviderService service;

    @Test
    void listReturnsProviders() throws Exception {
        LlmProvider p = provider(1L, "anthropic", true, true);
        when(service.list()).thenReturn(List.of(p));

        mvc.perform(get("/api/llm-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("anthropic"))
                .andExpect(jsonPath("$[0].providerType").value("ANTHROPIC_COMPATIBLE"));
    }

    @Test
    void getReturnsProvider() throws Exception {
        LlmProvider p = provider(1L, "anthropic", true, true);
        when(service.get(1L)).thenReturn(p);

        mvc.perform(get("/api/llm-providers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("anthropic"))
                .andExpect(jsonPath("$.providerType").value("ANTHROPIC_COMPATIBLE"));
    }

    @Test
    void getMissingReturns404() throws Exception {
        when(service.get(99L)).thenThrow(new ResourceNotFoundException("LlmProvider", 99));

        mvc.perform(get("/api/llm-providers/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturnsCreated() throws Exception {
        LlmProvider p = provider(1L, "new-provider", false, true);
        when(service.create(any(CreateLlmProviderRequest.class))).thenReturn(p);

        CreateLlmProviderRequest request = new CreateLlmProviderRequest();
        request.setName("new-provider");
        request.setProviderType("ANTHROPIC_COMPATIBLE");
        request.setApiKey("test-api-key");
        request.setBaseUrl("https://api.anthropic.com");
        request.setModelId("claude-3-5-sonnet");
        request.setEnabled(true);

        mvc.perform(post("/api/llm-providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("new-provider"));
    }

    @Test
    void updateReturnsProvider() throws Exception {
        LlmProvider p = provider(1L, "renamed", false, true);
        when(service.update(eq(1L), any(UpdateLlmProviderRequest.class))).thenReturn(p);

        UpdateLlmProviderRequest request = new UpdateLlmProviderRequest();
        request.setName("renamed");
        request.setEnabled(true);

        mvc.perform(put("/api/llm-providers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/llm-providers/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteBuiltinReturns409() throws Exception {
        doThrow(new InvalidStateException("内置 LLM 供应商禁止删除")).when(service).delete(1L);

        mvc.perform(delete("/api/llm-providers/1"))
                .andExpect(status().isConflict());
    }

    @Test
    void testConnectionReturnsResult() throws Exception {
        ConnectionTestResult result = new ConnectionTestResult(true, "连接成功", 120L);
        when(service.testConnection(1L)).thenReturn(result);

        mvc.perform(post("/api/llm-providers/1/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("连接成功"))
                .andExpect(jsonPath("$.latencyMs").value(120));
    }

    @Test
    void testConnectionUnsupportedTypeReturns409() throws Exception {
        when(service.testConnection(1L)).thenThrow(new InvalidStateException("不支持的供应商协议类型"));

        mvc.perform(post("/api/llm-providers/1/test"))
                .andExpect(status().isConflict());
    }

    @Test
    void setDefaultReturnsProvider() throws Exception {
        LlmProvider p = provider(1L, "new-default", true, true);
        when(service.setDefault(1L)).thenReturn(p);

        mvc.perform(put("/api/llm-providers/1/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void setDefaultDisabledReturns409() throws Exception {
        when(service.setDefault(1L)).thenThrow(new InvalidStateException("禁用的供应商不能设为默认"));

        mvc.perform(put("/api/llm-providers/1/default"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateEnabledReturnsProvider() throws Exception {
        LlmProvider p = provider(1L, "test", false, false);
        when(service.updateEnabled(1L, false)).thenReturn(p);

        mvc.perform(put("/api/llm-providers/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void updateEnabledDisableDefaultReturns409() throws Exception {
        when(service.updateEnabled(1L, false)).thenThrow(
                new InvalidStateException("默认供应商不能禁用，请先设置其他供应商为默认"));

        mvc.perform(put("/api/llm-providers/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isConflict());
    }

    private LlmProvider provider(Long id, String name, boolean isDefault, boolean enabled) {
        LlmProvider p = new LlmProvider();
        p.setId(id);
        p.setName(name);
        p.setProviderType(LlmProviderType.ANTHROPIC_COMPATIBLE);
        p.setApiKey("sk****1234");
        p.setBaseUrl("https://api.anthropic.com");
        p.setModelId("claude-3-5-sonnet");
        p.setMaxTokens(4096);
        p.setTimeoutSeconds(60L);
        p.setDefault(isDefault);
        p.setEnabled(enabled);
        p.setBuiltin(false);
        p.setSortOrder(0);
        p.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        p.setUpdatedAt(LocalDateTime.of(2025, 6, 1, 0, 0));
        return p;
    }
}
