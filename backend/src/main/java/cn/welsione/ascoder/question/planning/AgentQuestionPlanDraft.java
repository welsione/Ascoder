package cn.welsione.ascoder.question.planning;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Planner Agent 产出的结构化规划草稿，进入业务前必须经过校验和归一化。
 */
@Data
@NoArgsConstructor
public class AgentQuestionPlanDraft {
    private String type;
    private List<String> rewrittenQueries;
    private List<String> recommendedTools;
    private List<String> recommendedSkills;
    private String reasoning;
    private Double confidence;
    private List<String> matchedSignals;
    private List<String> alternativeTypes;
}
