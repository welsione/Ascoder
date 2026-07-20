package cn.welsione.ascoder.agent.infrastructure.agentscope;

import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Agent 工具集合，封装 Toolkit、SkillBox 和警告信息。
 */
@Value
@AllArgsConstructor
public class AgentTooling {
    Toolkit toolkit;
    SkillBox skillBox;
    List<String> warnings;
}
