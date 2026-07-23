package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.extension.config.CreateAgentConfigRequest;
import cn.welsione.ascoder.agent.extension.config.UpdateAgentConfigRequest;
import cn.welsione.ascoder.agent.persistence.AgentConfigJpaRepository;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentConfigService 集成测试：验证 Agent 配置 CRUD、唯一性校验、内置 Agent 保护与列表查询。
 *
 * <p>{@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。</p>
 */
@Transactional
class AgentConfigServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private AgentConfigService service;

    @Autowired
    private AgentConfigJpaRepository repository;

    private CreateAgentConfigRequest buildOrchestratorRequest(String agentId) {
        CreateAgentConfigRequest request = new CreateAgentConfigRequest();
        request.setAgentId(agentId);
        request.setDisplayName("测试编排Agent");
        request.setAgentRole(AgentRole.ORCHESTRATOR);
        request.setSystemPrompt("你是一个编排 Agent");
        request.setMaxIters(12);
        return request;
    }

    @Test
    void createAgentConfigSuccess() {
        CreateAgentConfigRequest request = buildOrchestratorRequest("test-create-orch");

        AgentConfig created = service.create(request);

        assertNotNull(created.getId());
        assertEquals("test-create-orch", created.getAgentId());
        assertEquals("测试编排Agent", created.getDisplayName());
        assertEquals(AgentRole.ORCHESTRATOR, created.getAgentRole());
        assertEquals("你是一个编排 Agent", created.getSystemPrompt());
        assertEquals(12, created.getMaxIters());

        AgentConfig persisted = repository.findById(created.getId()).orElseThrow();
        assertEquals("test-create-orch", persisted.getAgentId());
    }

    @Test
    void createDuplicateAgentIdThrowsValidationException() {
        service.create(buildOrchestratorRequest("test-dup"));

        assertThrows(ValidationException.class, () ->
                service.create(buildOrchestratorRequest("test-dup")));
    }

    @Test
    void createMissingSystemPromptThrowsValidationException() {
        CreateAgentConfigRequest request = buildOrchestratorRequest("test-no-prompt");
        request.setSystemPrompt("");

        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createNullRoleThrowsValidationException() {
        CreateAgentConfigRequest request = new CreateAgentConfigRequest();
        request.setAgentId("test-no-role");
        request.setDisplayName("测试");
        request.setSystemPrompt("系统提示词");

        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void updateAgentConfigSuccess() {
        AgentConfig created = service.create(buildOrchestratorRequest("test-update"));

        UpdateAgentConfigRequest update = new UpdateAgentConfigRequest();
        update.setAgentId("test-update");
        update.setDisplayName("更新后名称");
        update.setAgentRole(AgentRole.ORCHESTRATOR);
        update.setSystemPrompt("更新后系统提示词");
        update.setMaxIters(20);

        AgentConfig updated = service.update(created.getId(), update);

        assertEquals("更新后名称", updated.getDisplayName());
        assertEquals("更新后系统提示词", updated.getSystemPrompt());
        assertEquals(20, updated.getMaxIters());

        AgentConfig persisted = repository.findById(created.getId()).orElseThrow();
        assertEquals("更新后名称", persisted.getDisplayName());
        assertEquals(20, persisted.getMaxIters());
    }

    @Test
    void deleteNonBuiltinAgentSuccess() {
        AgentConfig created = service.create(buildOrchestratorRequest("test-delete"));

        service.delete(created.getId());

        assertTrue(repository.findById(created.getId()).isEmpty());
    }

    @Test
    void deleteBuiltinAgentThrowsInvalidStateException() {
        AgentConfig created = service.create(buildOrchestratorRequest("test-builtin"));
        created.setBuiltin(true);
        repository.save(created);

        assertThrows(InvalidStateException.class, () -> service.delete(created.getId()));
    }

    @Test
    void getThrowsWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(999999L));
    }

    @Test
    void listReturnsAllAgents() {
        long initialCount = service.list().size();

        service.create(buildOrchestratorRequest("test-list-1"));
        service.create(buildOrchestratorRequest("test-list-2"));
        service.create(buildOrchestratorRequest("test-list-3"));

        List<AgentConfig> all = service.list();
        assertEquals(initialCount + 3, all.size());
    }

    @Test
    void getByAgentIdReturnsConfig() {
        service.create(buildOrchestratorRequest("test-get-by-id"));

        AgentConfig found = service.getByAgentId("test-get-by-id").orElseThrow();
        assertEquals("test-get-by-id", found.getAgentId());
    }

    @Test
    void getByAgentIdReturnsEmptyWhenNotFound() {
        assertTrue(service.getByAgentId("nonexistent-agent").isEmpty());
    }
}
