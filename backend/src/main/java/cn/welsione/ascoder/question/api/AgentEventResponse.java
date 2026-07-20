package cn.welsione.ascoder.question.api;

import cn.welsione.ascoder.question.domain.AgentEvent;
import lombok.Getter;

import java.util.Date;

/**
 * Agent 事件响应，用于前端回放历史事件。
 */
@Getter
public class AgentEventResponse {

    private final Long id;
    private final Long questionId;
    private final int attemptNo;
    private final int sequenceNo;
    private final String eventType;
    private final Object payload;
    private final Date createdAt;

    public AgentEventResponse(AgentEvent event, Object parsedPayload) {
        this.id = event.getId();
        this.questionId = event.getQuestionId();
        this.attemptNo = event.getAttemptNo();
        this.sequenceNo = event.getSequenceNo();
        this.eventType = event.getEventType();
        this.payload = parsedPayload;
        this.createdAt = event.getCreatedAt();
    }
}
