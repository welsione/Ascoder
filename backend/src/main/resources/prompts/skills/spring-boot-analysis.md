# Spring Boot Analysis

适用场景：
- 查找后端入口类、Controller、Service、Repository、配置类。
- 分析接口请求流程、业务流程、启动配置和依赖注入。

推荐 CodeGraph 工具顺序：
1. 查入口：先 `codegraph_search` 搜索 `SpringBootApplication Application main`，再用 `codegraph_context` 确认代码。
2. 查接口流程：先搜索 `Controller RequestMapping RestController`，再追踪 Controller 到 Service。
3. 查配置：优先查看 `application.yml`、`@Configuration`、`@ConfigurationProperties`。
4. 查调用关系：已知道符号时使用 `codegraph_callers` 或 `codegraph_callees`。

回答要求：
- 说明入口类、关键注解、关键方法和文件路径。
- 不要只说“通常是”，必须基于 CodeGraph 结果给出代码证据。
