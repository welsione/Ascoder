package cn.welsione.ascoder.agent.extension.config;

import cn.welsione.ascoder.agent.application.AgentRunRecordService;
import cn.welsione.ascoder.agent.domain.AgentRunRecord;
import cn.welsione.ascoder.agent.infrastructure.agentscope.AgentStatusSseManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 运行记录与实时状态 REST 控制器。
 *
 * <p>提供运行历史分页查询、单条详情和 SSE 实时状态流。
 * SSE 端点复用 {@link AgentStatusSseManager}，空闲 60s 自动断开。</p>
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentRunController {

    private final AgentRunRecordService runRecordService;
    private final AgentStatusSseManager sseManager;

    @GetMapping("/{agentId}/runs")
    public Page<AgentRunRecord> listRuns(@PathVariable String agentId, Pageable pageable) {
        return runRecordService.listByAgent(agentId, pageable);
    }

    @GetMapping("/{agentId}/runs/{runId}")
    public AgentRunRecord getRun(@PathVariable String agentId, @PathVariable Long runId) {
        return runRecordService.get(runId);
    }

    @GetMapping(value = "/{agentId}/status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeStatus(@PathVariable String agentId) {
        return sseManager.subscribe(agentId);
    }
}
