package cn.welsione.ascoder.agent.port;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.agent.domain.AgentStreamEvent;
import reactor.core.publisher.Flux;

/**
 * 代码回答 Agent 端口，提供基于事件流的回答方式。
 */
public interface CodeAnswerAgent {

    /**
     * 流式回答问题，返回 Ascoder 自有事件流。
     */
    Flux<AgentStreamEvent> streamAnswer(AgentRequest request);
}
