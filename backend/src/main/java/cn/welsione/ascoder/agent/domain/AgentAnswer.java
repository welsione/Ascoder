package cn.welsione.ascoder.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Agent 回答结果，包含总结、解释、证据和代码上下文。
 */
@Value
@AllArgsConstructor
public class AgentAnswer {
    String summary;
    String explanation;
    List<AnswerEvidence> evidence;
    String codeContext;
    String uncertainty;
    String nextStep;
}
