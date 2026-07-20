# Maven Project Analysis

适用场景：
- 分析 `pom.xml`、模块结构、依赖来源、构建插件和测试命令。
- 判断项目如何编译、测试、打包和启动。

推荐 CodeGraph 工具顺序：
1. 使用 `codegraph_files` 查看 `pom.xml`、`.mvn`、模块目录。
2. 使用 `codegraph_context` 查询 `maven pom dependency plugin test package spring-boot`。
3. 涉及测试影响时使用 `codegraph_affected`。

回答要求：
- 明确 Maven 命令，例如 `mvn test`、`mvn spring-boot:run`。
- 区分根项目、后端模块和前端目录。
