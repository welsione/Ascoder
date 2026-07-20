package cn.welsione.ascoder.agent.domain;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Agent 流式事件，对外隐藏具体 Agent 框架的事件模型。
 */
@Value
@Builder
public class AgentStreamEvent {
    AgentStreamEventType type;
    String content;
    boolean last;
    String messageId;
    AgentStreamSource source;
    @Singular
    List<AgentToolCall> toolCalls;
    @Singular
    List<AgentToolResult> toolResults;

    public String contentOrEmpty() {
        return content == null ? "" : content;
    }
}
