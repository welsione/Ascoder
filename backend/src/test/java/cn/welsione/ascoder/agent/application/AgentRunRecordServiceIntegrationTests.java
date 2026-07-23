package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRunRecord;
import cn.welsione.ascoder.agent.domain.AgentRunStatus;
import cn.welsione.ascoder.agent.persistence.AgentRunRecordJpaRepository;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentRunRecordService 集成测试：验证运行记录 CRUD、状态流转与启动时中断修复。
 *
 * <p>{@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。</p>
 */
@Transactional
class AgentRunRecordServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private AgentRunRecordService service;

    @Autowired
    private AgentRunRecordJpaRepository repository;

    @Autowired
    private IntegrationTestDataFactory dataFactory;

    @Test
    void startCreatesRunningRecord() {
        AgentConfig config = dataFactory.createAgentConfig("agent-1");

        AgentRunRecord record = service.start("agent-1", config.getId(), 100L, 200L, 1, "输入摘要");

        assertNotNull(record.getId());
        AgentRunRecord saved = repository.findById(record.getId()).orElseThrow();
        assertEquals(AgentRunStatus.RUNNING, saved.getStatus());
        assertEquals("agent-1", saved.getAgentId());
        assertEquals(config.getId(), saved.getAgentConfigId());
        assertEquals(100L, saved.getQuestionId());
        assertEquals(200L, saved.getConversationId());
        assertEquals(1, saved.getAttemptNo());
        assertEquals("输入摘要", saved.getInputSummary());
        assertNotNull(saved.getStartedAt());
        assertNull(saved.getFinishedAt());
    }

    @Test
    void finishUpdatesStatusAndDuration() throws Exception {
        AgentConfig config = dataFactory.createAgentConfig("agent-2");
        AgentRunRecord record = service.start("agent-2", config.getId(), 101L, 201L, 1, "输入");
        Thread.sleep(10); // 确保 duration > 0

        service.finish(record.getId(), AgentRunStatus.SUCCEEDED, "输出摘要", 3, 5, null);

        AgentRunRecord saved = repository.findById(record.getId()).orElseThrow();
        assertEquals(AgentRunStatus.SUCCEEDED, saved.getStatus());
        assertEquals("输出摘要", saved.getOutputSummary());
        assertEquals(3, saved.getToolCallCount());
        assertEquals(5, saved.getIterCount());
        assertNull(saved.getErrorMessage());
        assertNotNull(saved.getFinishedAt());
        assertTrue(saved.getDurationMs() >= 10);
    }

    @Test
    void finishWithFailedStatusRecordsError() {
        AgentConfig config = dataFactory.createAgentConfig("agent-3");
        AgentRunRecord record = service.start("agent-3", config.getId(), 102L, 202L, 1, "输入");

        service.finish(record.getId(), AgentRunStatus.FAILED, null, 0, 2, "执行异常");

        AgentRunRecord saved = repository.findById(record.getId()).orElseThrow();
        assertEquals(AgentRunStatus.FAILED, saved.getStatus());
        assertEquals("执行异常", saved.getErrorMessage());
        assertNotNull(saved.getFinishedAt());
    }

    @Test
    void getThrowsWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(999999L));
    }

    @Test
    void listByAgentReturnsPagedRecords() {
        AgentConfig config = dataFactory.createAgentConfig("agent-list");
        service.start("agent-list", config.getId(), 1L, 1L, 1, "a");
        service.start("agent-list", config.getId(), 2L, 1L, 2, "b");
        service.start("agent-list", config.getId(), 3L, 1L, 3, "c");
        service.start("agent-other", config.getId(), 4L, 1L, 1, "d");

        var page = service.listByAgent("agent-list", PageRequest.of(0, 10));

        assertEquals(3, page.getTotalElements());
        assertTrue(page.getContent().stream().allMatch(r -> "agent-list".equals(r.getAgentId())));
        // 按开始时间倒序，最新在前
        assertEquals(3, page.getContent().get(0).getAttemptNo());
    }

    @Test
    void countByAgentReturnsCorrectCount() {
        AgentConfig config = dataFactory.createAgentConfig("agent-count");
        service.start("agent-count", config.getId(), 1L, 1L, 1, "a");
        service.start("agent-count", config.getId(), 2L, 1L, 2, "b");

        assertEquals(2, service.countByAgent("agent-count"));
        assertEquals(0, service.countByAgent("agent-none"));
    }

    @Test
    void repairInterruptedConvertsDanglingRunningToInterrupted() {
        AgentConfig config = dataFactory.createAgentConfig("agent-dangling");
        // 直接在 DB 插入一条 finishedAt 为空的 RUNNING 记录（模拟进程崩溃残留）
        AgentRunRecord dangling = new AgentRunRecord();
        dangling.setAgentId("agent-dangling");
        dangling.setAgentConfigId(config.getId());
        dangling.setQuestionId(1L);
        dangling.setConversationId(1L);
        dangling.setAttemptNo(1);
        dangling.setStatus(AgentRunStatus.RUNNING);
        dangling.setInputSummary("未完成");
        dangling.setStartedAt(LocalDateTime.now().minusMinutes(5));
        repository.save(dangling);

        service.repairInterrupted();

        AgentRunRecord repaired = repository.findById(dangling.getId()).orElseThrow();
        assertEquals(AgentRunStatus.INTERRUPTED, repaired.getStatus());
        assertEquals("进程重启前未正常结束，已修正为中断状态", repaired.getErrorMessage());
        assertNotNull(repaired.getFinishedAt());
    }

    @Test
    void repairInterruptedNoopWhenNoDangling() {
        assertDoesNotThrow(() -> service.repairInterrupted());
    }
}
