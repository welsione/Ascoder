package cn.welsione.ascoder.question.web;

import cn.welsione.ascoder.question.api.AgentEventResponse;
import cn.welsione.ascoder.question.api.CreateQuestionRequest;
import cn.welsione.ascoder.question.api.QuestionResponse;
import cn.welsione.ascoder.question.application.AgentEventService;
import cn.welsione.ascoder.question.application.QuestionService;
import cn.welsione.ascoder.question.application.QuestionService.PendingQuestion;
import cn.welsione.ascoder.question.domain.AgentEvent;
import cn.welsione.ascoder.question.stream.QuestionStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 问题 REST 控制器，提供问题提交和查询接口，支持 SSE 流式回答。
 */
@Slf4j
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionStreamService questionStreamService;
    private final AgentEventService agentEventService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<QuestionResponse> list() {
        return questionService.list();
    }

    @GetMapping("/conversations/{conversationId}")
    public List<QuestionResponse> listByConversation(@PathVariable Long conversationId) {
        return questionService.listByConversation(conversationId);
    }

    @GetMapping("/{id}")
    public QuestionResponse get(@PathVariable Long id) {
        return questionService.get(id);
    }

    /**
     * 查询指定问题的 Agent 事件列表，用于前端回放分析过程。
     */
    @GetMapping("/{id}/agent-events")
    public List<AgentEventResponse> listAgentEvents(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int attemptNo) {
        int effectiveAttempt = attemptNo > 0 ? attemptNo : agentEventService.latestAttemptNo(id);
        List<AgentEvent> events = agentEventService.listEvents(id, effectiveAttempt);
        return events.stream()
                .map(event -> new AgentEventResponse(event, parsePayload(event)))
                .toList();
    }

    /**
     * 流式提交问题，返回 SSE 连接，Agent 回答事件实时推送。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody CreateQuestionRequest request) {
        // 1. 同步创建问题记录，并使用同一份查询规划构建 Agent 请求
        PendingQuestion pending = questionService.prepareStream(request);
        QuestionResponse initial = pending.getQuestion();
        Long questionId = initial.getId();

        // 2. 创建 SSE emitter
        SseEmitter emitter = questionStreamService.createEmitter(questionId);

        // 3. 启动异步流式处理
        questionStreamService.askAsync(questionId, request, pending.getAgentRequest());

        return emitter;
    }

    /**
     * 恢复已有问题的流式任务，只允许恢复状态仍为 RUNNING 的问题。
     */
    @PostMapping(value = "/{id}/stream/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeStream(@PathVariable Long id) {
        SseEmitter emitter = questionStreamService.createEmitter(id);
        questionStreamService.resumeAsync(id);
        return emitter;
    }

    /**
     * 重试失败的问题，失败状态必须由用户显式触发才能再次执行。
     */
    @PostMapping(value = "/{id}/stream/retry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retryStream(@PathVariable Long id) {
        SseEmitter emitter = questionStreamService.createEmitter(id);
        questionStreamService.retryAsync(id);
        return emitter;
    }

    /**
     * 用户主动停止流式回答，终止后台任务并返回最新问题状态。
     */
    @PostMapping("/{id}/stream/cancel")
    public QuestionResponse cancelStream(@PathVariable Long id) {
        return questionStreamService.cancel(id);
    }

    private Object parsePayload(AgentEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), Object.class);
        } catch (Exception ex) {
            return event.getPayload();
        }
    }
}
