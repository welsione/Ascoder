package cn.welsione.ascoder.agent.extension.skill;

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
 * Agent 技能 REST 控制器，提供技能 CRUD 接口。
 */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class AgentSkillController {

    private final AgentSkillService skillService;

    @GetMapping
    public List<AgentSkillConfig> list() {
        return skillService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentSkillConfig create(@Valid @RequestBody CreateAgentSkillRequest request) {
        return skillService.create(request);
    }

    @PatchMapping("/{id}/enabled")
    public AgentSkillConfig updateEnabled(
            @PathVariable Long id,
            @RequestBody UpdateAgentSkillEnabledRequest request
    ) {
        return skillService.updateEnabled(id, request);
    }
}
