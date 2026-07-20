package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentRunStatus;
import cn.welsione.ascoder.agent.domain.AgentRuntimeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AgentRuntimeRegistry 状态机与事件发布测试。
 */
@ExtendWith(MockitoExtension.class)
class AgentRuntimeRegistryTests {

    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AgentRuntimeRegistry registry;

    @Test
    void markRunningSetsRunningAndPublishesEvent() {
        registry.markRunning("code-researcher", 100L, 1L);

        assertEquals(AgentRuntimeStatus.RUNNING, registry.status("code-researcher"));
        ArgumentCaptor<AgentStatusChangedEvent> captor = ArgumentCaptor.forClass(AgentStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(AgentRuntimeStatus.RUNNING, captor.getValue().getStatus());
        assertEquals(1L, captor.getValue().getRunRecordId());
    }

    @Test
    void markIdleClearsStateAndPublishesIdleEvent() {
        registry.markRunning("code-researcher", 100L, 1L);
        registry.markIdle("code-researcher", 1L, AgentRunStatus.SUCCEEDED);

        assertEquals(AgentRuntimeStatus.IDLE, registry.status("code-researcher"));
        verify(eventPublisher, times(2)).publishEvent(any(AgentStatusChangedEvent.class));
    }

    @Test
    void unknownAgentIsIdle() {
        assertEquals(AgentRuntimeStatus.IDLE, registry.status("unknown"));
    }

    @Test
    void snapshotReflectsRunningAgents() {
        registry.markRunning("agent-a", 1L, 10L);
        registry.markRunning("agent-b", 2L, 20L);

        assertEquals(2, registry.snapshot().size());
        assertTrue(registry.snapshot().stream().anyMatch(v -> v.getAgentId().equals("agent-a")));
    }

    @Test
    void markIdleRemovesFromSnapshot() {
        registry.markRunning("agent-a", 1L, 10L);
        registry.markIdle("agent-a", 10L, AgentRunStatus.SUCCEEDED);

        assertTrue(registry.snapshot().isEmpty());
    }
}
