package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.application.AgentStatusChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Agent 状态 SSE 推送管理器，维护每个 Agent 的 SseEmitter 集合。
 *
 * <p>监听 {@link AgentStatusChangedEvent}（Spring @EventListener，同步执行），
 * 向对应 agentId 的所有订阅者推送状态变更事件。SseEmitter 超时 60s 自动断开。</p>
 */
@Slf4j
@Component
public class AgentStatusSseManager {

    private static final long SSE_TIMEOUT = 60_000L;

    private final Map<String, Set<SseEmitter>> agentEmitters = new ConcurrentHashMap<>();

    /**
     * 订阅指定 Agent 的状态变更 SSE 流。
     */
    public SseEmitter subscribe(String agentId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        agentEmitters.computeIfAbsent(agentId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(agentId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(agentId, emitter);
        });
        emitter.onError(ex -> removeEmitter(agentId, emitter));

        log.debug("SSE 订阅，agentId={}，当前订阅数={}", agentId, agentEmitters.getOrDefault(agentId, Set.of()).size());
        return emitter;
    }

    @EventListener
    public void onStatusChanged(AgentStatusChangedEvent event) {
        Set<SseEmitter> emitters = agentEmitters.get(event.getAgentId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        String data = String.format("{\"agentId\":\"%s\",\"status\":\"%s\",\"runRecordId\":%s}",
                event.getAgentId(), event.getStatus(), event.getRunRecordId());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("status").data(data));
            } catch (IOException ex) {
                log.debug("SSE 推送失败，agentId={}，移除 emitter", event.getAgentId());
                removeEmitter(event.getAgentId(), emitter);
            }
        }
        log.debug("SSE 状态推送完成，agentId={}，status={}，订阅数={}",
                event.getAgentId(), event.getStatus(), emitters.size());
    }

    private void removeEmitter(String agentId, SseEmitter emitter) {
        Set<SseEmitter> emitters = agentEmitters.get(agentId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}
