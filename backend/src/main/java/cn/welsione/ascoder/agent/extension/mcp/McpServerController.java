package cn.welsione.ascoder.agent.extension.mcp;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP 服务器 REST 控制器，提供 MCP 服务器配置的 CRUD 接口。
 */
@RestController
@RequestMapping("/api/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;

    @GetMapping
    public List<McpServerConfig> list() {
        return mcpServerService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public McpServerConfig create(@Valid @RequestBody CreateMcpServerRequest request) {
        return mcpServerService.create(request);
    }

    @PatchMapping("/{id}/enabled")
    public McpServerConfig updateEnabled(
            @PathVariable Long id,
            @RequestBody UpdateMcpServerEnabledRequest request
    ) {
        return mcpServerService.updateEnabled(id, request);
    }
}
