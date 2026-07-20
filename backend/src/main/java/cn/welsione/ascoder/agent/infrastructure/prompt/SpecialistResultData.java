package cn.welsione.ascoder.agent.infrastructure.prompt;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 最终汇总提示词中的 Specialist Agent 输出项。
 */
@Data
@AllArgsConstructor
public class SpecialistResultData {
    String agentId;
    String agentName;
    String result;
}
