package cn.welsione.ascoder.agent.extension.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 工具 REST 控制器，提供内置工具目录和启停管理接口。
 */
@RestController
@RequestMapping("/api/agent-tools")
@RequiredArgsConstructor
public class AgentToolController {

    private final AgentToolService toolService;

    @GetMapping
    public List<AgentToolConfig> list() {
        return toolService.list();
    }

    @PatchMapping("/{id}/enabled")
    public AgentToolConfig updateEnabled(
            @PathVariable Long id,
            @RequestBody UpdateAgentToolEnabledRequest request
    ) {
        return toolService.updateEnabled(id, request);
    }
}
