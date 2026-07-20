package cn.welsione.ascoder.agent.web;

import cn.welsione.ascoder.agent.application.LlmProviderService;
import cn.welsione.ascoder.agent.domain.ConnectionTestResult;
import cn.welsione.ascoder.agent.domain.LlmProvider;
import cn.welsione.ascoder.agent.extension.config.CreateLlmProviderRequest;
import cn.welsione.ascoder.agent.extension.config.UpdateLlmProviderRequest;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LLM 供应商 REST 控制器，提供 CRUD、连接测试、默认切换与启停接口。
 */
@RestController
@RequestMapping("/api/llm-providers")
@RequiredArgsConstructor
@Slf4j
public class LlmProviderController {

    private final LlmProviderService service;

    @GetMapping
    public List<LlmProviderResponse> list() {
        return service.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public LlmProviderResponse get(@PathVariable Long id) {
        return toResponse(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LlmProviderResponse create(@Valid @RequestBody CreateLlmProviderRequest request) {
        return toResponse(service.create(request));
    }

    @PutMapping("/{id}")
    public LlmProviderResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLlmProviderRequest request) {
        return toResponse(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/test")
    public ConnectionTestResult testConnection(@PathVariable Long id) {
        return service.testConnection(id);
    }

    @PutMapping("/{id}/default")
    public LlmProviderResponse setDefault(@PathVariable Long id) {
        return toResponse(service.setDefault(id));
    }

    @PutMapping("/{id}/enabled")
    public LlmProviderResponse updateEnabled(@PathVariable Long id, @RequestBody UpdateEnabledRequest request) {
        return toResponse(service.updateEnabled(id, request.isEnabled()));
    }

    private LlmProviderResponse toResponse(LlmProvider provider) {
        LlmProviderResponse resp = new LlmProviderResponse();
        resp.setId(provider.getId());
        resp.setName(provider.getName());
        resp.setProviderType(provider.getProviderType().name());
        resp.setApiKey(provider.getApiKey());
        resp.setBaseUrl(provider.getBaseUrl());
        resp.setModelId(provider.getModelId());
        resp.setMaxTokens(provider.getMaxTokens());
        resp.setTimeoutSeconds(provider.getTimeoutSeconds());
        resp.setDefault(provider.isDefault());
        resp.setEnabled(provider.isEnabled());
        resp.setBuiltin(provider.isBuiltin());
        resp.setSortOrder(provider.getSortOrder());
        resp.setCreatedAt(formatDateTime(provider.getCreatedAt()));
        resp.setUpdatedAt(formatDateTime(provider.getUpdatedAt()));
        return resp;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }

    @Data
    static class UpdateEnabledRequest {
        private boolean enabled;
    }
}
