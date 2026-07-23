package cn.welsione.ascoder;

import cn.welsione.ascoder.common.task.AsyncTaskJpaRepository;
import cn.welsione.ascoder.common.task.NoopTaskDefinition;
import cn.welsione.ascoder.common.task.TaskEngine;
import cn.welsione.ascoder.common.task.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

/**
 * 集成测试基类：启动完整 Spring 上下文，连接 docker 化测试数据库（3307 端口）。
 *
 * <p>前置条件：</p>
 * <ol>
 *   <li>启动测试数据库：{@code docker compose -f docker-compose.test.yml up -d mysql}</li>
 *   <li>设置环境变量：{@code export ASCODER_INTEGRATION_TEST=true}（避免无数据库环境误跑）</li>
 * </ol>
 *
 * <p>运行：{@code ASCODER_INTEGRATION_TEST=true mvn test -pl backend -Dtest=TaskEngineIntegrationTests}</p>
 *
 * <p>默认 {@code mvn test} 不运行集成测试（环境变量未设），不影响 CI。</p>
 *
 * <p>{@link #cleanupAllTables} 在每个测试后兜底清理所有业务表，
 * 确保异步任务（不参与 {@code @Transactional} 回滚）产生的残留不污染后续测试。
 * 对于 {@code @Transactional} 测试，回滚后表已空，清理为 no-op。</p>
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration-test")
@Import(NoopTaskDefinition.class)
@EnabledIfEnvironmentVariable(named = "ASCODER_INTEGRATION_TEST", matches = "true")
@DisplayName("异步任务框架集成测试")
public abstract class AbstractIntegrationTest {

    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private AsyncTaskJpaRepository asyncTaskRepository;

    /** 需要兜底清理的业务表（排除 flyway_schema_history）。 */
    private static final List<String> TABLES_TO_CLEAN = List.of(
            "asyncTasks", "agentEvent", "agentRunRecords", "logEvidenceRefs", "logFiles",
            "logUploads", "logAnalysisTasks", "queryPlans", "questions", "conversations",
            "learningCorrections", "learningExperiences", "learningKnowledgeRelations",
            "learningKnowledgeItems", "learningRawEvents", "learningInsights",
            "learningAgentRuns", "learningTerms", "selfLearningSettings",
            "projectSpaceMembers", "branchWorkspaces", "repositoryBranches",
            "projectRepositories", "projectSpaces", "projects", "repositories",
            "agentToolConfigs", "agent_skills", "mcp_servers", "agentConfigs",
            "llmProvider", "systemSettings");

    @Autowired
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void cleanupAllTables() {
        // 先取消所有未完成的异步任务，避免 DELETE 与异步 execute 竞态
        try {
            asyncTaskRepository.findByStatusIn(List.of(TaskStatus.QUEUED, TaskStatus.RUNNING))
                    .forEach(t -> {
                        try {
                            taskEngine.cancel(t.getId());
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
        // 兜底清理：禁用外键检查，逐表删除，再启用。确保异步任务残留不污染后续测试。
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : TABLES_TO_CLEAN) {
            try {
                jdbcTemplate.execute("DELETE FROM " + table);
            } catch (Exception ignored) {
                // 表可能不存在（Flyway 未迁移到），跳过
            }
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}
