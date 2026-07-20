package cn.welsione.ascoder.agent.extension.config;

import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.ascoder.agent.application.AgentRuntimeRegistry;
import cn.welsione.ascoder.agent.application.AgentRuntimeView;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRuntimeStatus;
import cn.welsione.ascoder.agent.infrastructure.prompt.TaskPromptContext;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 配置 REST 控制器，提供 CRUD、启停、模板预览与运行态快照接口。
 *
 * <p>风格与现有 {@link cn.welsione.ascoder.agent.extension.tool.AgentToolController} 一致：
 * 直接返回实体/列表，无响应包装，错误由 {@code GlobalExceptionHandler} 统一处理。</p>
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentConfigController {

    private final AgentConfigService service;
    private final AgentRuntimeRegistry runtimeRegistry;

    @GetMapping
    public List<AgentConfigView> list() {
        List<AgentConfig> configs = service.list();
        Map<String, AgentRuntimeStatus> statuses = runtimeRegistry.snapshot().stream()
                .collect(Collectors.toMap(AgentRuntimeView::getAgentId, AgentRuntimeView::getStatus));
        return configs.stream()
                .map(config -> new AgentConfigView(config, statuses.getOrDefault(config.getAgentId(), AgentRuntimeStatus.IDLE)))
                .toList();
    }

    @GetMapping("/{id}")
    public AgentConfig get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentConfig create(@Valid @RequestBody CreateAgentConfigRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public AgentConfig update(@PathVariable Long id, @Valid @RequestBody UpdateAgentConfigRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PatchMapping("/{id}/enabled")
    public AgentConfig updateEnabled(@PathVariable Long id, @RequestBody UpdateAgentEnabledRequest request) {
        return service.updateEnabled(id, request.isEnabled());
    }

    @PostMapping("/{id}/test-render")
    public TestRenderResponse testRender(@PathVariable Long id, @RequestBody TestRenderRequest request) {
        return service.testRender(id, request.getSampleContext());
    }

    @GetMapping("/status")
    public List<AgentRuntimeView> statuses() {
        return runtimeRegistry.snapshot();
    }

    @Data
    static class UpdateAgentEnabledRequest {
        private boolean enabled;
    }
}
