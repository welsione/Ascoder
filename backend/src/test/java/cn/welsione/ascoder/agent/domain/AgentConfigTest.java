package cn.welsione.ascoder.agent.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentConfig 实体 JSON 字段读写与便捷方法解析测试。
 */
class AgentConfigTest {

    @Test
    void parsesRoleKeysFromJson() {
        AgentConfig config = new AgentConfig();
        config.setRoleKeysJson("[\"tester\",\"developer\"]");

        assertEquals(java.util.List.of("tester", "developer"), config.getRoleKeys());
    }

    @Test
    void parsesQuestionKeywordsFromJson() {
        AgentConfig config = new AgentConfig();
        config.setQuestionKeywordsJson("[\"影响\",\"风险\"]");

        assertEquals(java.util.List.of("影响", "风险"), config.getQuestionKeywords());
    }

    @Test
    void parsesToolGroupKeysFromJson() {
        AgentConfig config = new AgentConfig();
        config.setToolGroupKeysJson("[\"codegraph\",\"git\",\"file\"]");

        assertEquals(java.util.List.of("codegraph", "git", "file"), config.getToolGroupKeys());
    }

    @Test
    void emptyJsonReturnsEmptyList() {
        AgentConfig config = new AgentConfig();

        assertTrue(config.getRoleKeys().isEmpty());
        assertTrue(config.getSkillNames().isEmpty());
        assertTrue(config.getMcpServerNames().isEmpty());
    }

    @Test
    void blankJsonReturnsEmptyList() {
        AgentConfig config = new AgentConfig();
        config.setRoleKeysJson("   ");

        assertTrue(config.getRoleKeys().isEmpty());
    }

    @Test
    void invalidJsonReturnsEmptyList() {
        AgentConfig config = new AgentConfig();
        config.setRoleKeysJson("not a json");

        assertTrue(config.getRoleKeys().isEmpty());
    }

    @Test
    void defaultsAreApplied() {
        AgentConfig config = new AgentConfig();

        assertEquals(12, config.getMaxIters());
        assertFalse(config.isRequired());
        assertTrue(config.isEnabled());
        assertFalse(config.isBuiltin());
        assertEquals(0, config.getSortOrder());
        assertEquals(0L, config.getVersion());
    }

    @Test
    void enumFieldsPersist() {
        AgentConfig config = new AgentConfig();
        config.setAgentRole(AgentRole.ORCHESTRATOR);
        config.setTaskKind(SpecialistTaskKind.CODE_RESEARCH);

        assertEquals(AgentRole.ORCHESTRATOR, config.getAgentRole());
        assertEquals(SpecialistTaskKind.CODE_RESEARCH, config.getTaskKind());
    }

    @Test
    void orchestratorAllowsNullTaskKind() {
        AgentConfig config = new AgentConfig();
        config.setAgentRole(AgentRole.ORCHESTRATOR);
        config.setTaskKind(null);

        assertNull(config.getTaskKind());
    }
}
