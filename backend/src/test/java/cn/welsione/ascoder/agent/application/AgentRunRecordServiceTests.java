package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentRunRecord;
import cn.welsione.ascoder.agent.domain.AgentRunStatus;
import cn.welsione.ascoder.agent.persistence.AgentRunRecordJpaRepository;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AgentRunRecordService 运行记录生命周期与中断修复测试。
 */
@ExtendWith(MockitoExtension.class)
class AgentRunRecordServiceTests {

    @Mock private AgentRunRecordJpaRepository repository;

    @InjectMocks
    private AgentRunRecordService service;

    @Test
    void startCreatesRunningRecord() {
        when(repository.save(any(AgentRunRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentRunRecord record = service.start("code-researcher", 1L, 100L, 200L, 1, "问题摘要");

        ArgumentCaptor<AgentRunRecord> captor = ArgumentCaptor.forClass(AgentRunRecord.class);
        verify(repository).save(captor.capture());
        assertEquals("code-researcher", captor.getValue().getAgentId());
        assertEquals(AgentRunStatus.RUNNING, captor.getValue().getStatus());
        assertEquals(100L, captor.getValue().getQuestionId());
        assertNotNull(captor.getValue().getStartedAt());
        assertEquals("问题摘要", record.getInputSummary());
    }

    @Test
    void finishUpdatesStatusAndDuration() {
        AgentRunRecord record = new AgentRunRecord();
        record.setId(1L);
        record.setStartedAt(java.time.LocalDateTime.now().minusSeconds(2));
        when(repository.findById(1L)).thenReturn(Optional.of(record));

        service.finish(1L, AgentRunStatus.SUCCEEDED, "结果摘要", 5, 3, null);

        assertEquals(AgentRunStatus.SUCCEEDED, record.getStatus());
        assertEquals("结果摘要", record.getOutputSummary());
        assertEquals(5, record.getToolCallCount());
        assertNotNull(record.getFinishedAt());
        assertNotNull(record.getDurationMs());
        verify(repository).save(record);
    }

    @Test
    void listByAgentReturnsPage() {
        Page<AgentRunRecord> page = new PageImpl<>(List.of(new AgentRunRecord()));
        when(repository.findByAgentIdOrderByStartedAtDesc("agent", PageRequest.of(0, 10))).thenReturn(page);

        Page<AgentRunRecord> result = service.listByAgent("agent", PageRequest.of(0, 10));
        assertEquals(1, result.getContent().size());
    }

    @Test
    void countByAgentDelegatesToRepository() {
        when(repository.countByAgentId("agent")).thenReturn(42L);
        assertEquals(42L, service.countByAgent("agent"));
    }

    @Test
    void getMissingThrows() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(99L));
    }

    @Test
    void repairInterruptedFixesDanglingRecords() {
        AgentRunRecord dangling = new AgentRunRecord();
        dangling.setStatus(AgentRunStatus.RUNNING);
        when(repository.findByStatusAndFinishedAtIsNull(AgentRunStatus.RUNNING)).thenReturn(List.of(dangling));

        service.repairInterrupted();

        assertEquals(AgentRunStatus.INTERRUPTED, dangling.getStatus());
        assertNotNull(dangling.getFinishedAt());
        verify(repository).saveAll(List.of(dangling));
    }

    @Test
    void repairInterruptedNoOpWhenNone() {
        when(repository.findByStatusAndFinishedAtIsNull(AgentRunStatus.RUNNING)).thenReturn(List.of());
        service.repairInterrupted();
        verify(repository, never()).saveAll(any());
    }
}
