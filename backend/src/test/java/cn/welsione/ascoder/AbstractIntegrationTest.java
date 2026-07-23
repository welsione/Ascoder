package cn.welsione.ascoder;

import cn.welsione.ascoder.common.task.NoopTaskDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

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
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration-test")
@Import(NoopTaskDefinition.class)
@EnabledIfEnvironmentVariable(named = "ASCODER_INTEGRATION_TEST", matches = "true")
@DisplayName("异步任务框架集成测试")
public abstract class AbstractIntegrationTest {
}
