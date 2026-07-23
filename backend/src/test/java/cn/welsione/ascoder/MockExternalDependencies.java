package cn.welsione.ascoder;

import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.repository.git.GitCredentialStore;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * 集成测试外部依赖 Mock 配置。
 *
 * <p>集中 mock 调用外部进程/文件系统特定路径的 Bean，使集成测试聚焦业务逻辑 + 真实 DB，
 * 不触发真实 git CLI / codegraph CLI / ~/.git-credentials 文件写入。</p>
 *
 * <p>需要 mock 外部依赖的集成测试类用 {@code @Import(MockExternalDependencies.class)} 导入，
 * 然后 {@code @Autowired} 注入 mock Bean 配置 stub。</p>
 */
@TestConfiguration
@MockBean({GitRepositoryService.class, CodeGraphClient.class, GitCredentialStore.class})
public class MockExternalDependencies {
}
