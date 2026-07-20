# CLAUDE.md

## 项目全景

Ascoder 是一个代码智能分析平台，帮助开发者对代码仓库进行结构化问答、索引与分析。

- **后端**：Java 17 / Spring Boot 3.3 / JPA / Flyway / MySQL 8.4 / Maven
- **前端**：Vue 3 / TypeScript / Pinia / Vue Router / Element Plus / Vite
- **构建**：Maven（backend），Vite（frontend），Docker Compose（部署）

## 模块边界

后端逻辑模块按依赖方向组织：

```
app → chat → agent → analysis → repository → common
```

- **common**：领域异常、安全校验、命令执行、工具类。不依赖任何业务模块。
- **repository**：Git 操作、Project / ProjectSpace / Workspace 管理。
- **analysis**：CodeGraph 接入、索引同步、符号查询、代码证据提取。
- **agent**：Agent 构建编排、Prompt 管理、工具装配、Skill / MCP 扩展。
- **chat**：Question / Conversation 管理、问答流程、SSE 流式推送。

**边界约束**：
- 跨聚合操作通过领域事件解耦，禁止在一个 Service 中直接操作另一个聚合的 Repository。
- 对外端口不得暴露 AgentScope、Anthropic 等第三方运行时类型。
- 新代码必须放入正确的模块包，不确定时先确认再动手。

> 物理 Maven 模块见根 `pom.xml` 的 `<modules>` 节点（合并后仅 `jprompt` + `backend`）。逻辑模块与 Maven 物理拆分解耦：代码包按逻辑模块组织，但不再拆为独立 jar。

## 面向对象与代码复用

### 七大原则

- **单一职责（SRP）**：一个类只做一件事，一个方法只做一件事。超过 80 行的方法应考虑拆分。
- **开闭原则（OCP）**：通过继承或组合扩展行为，不修改已有代码。新增能力优先用策略模式 / 接口 + 实现。
- **里氏替换（LSP）**：子类可以替换父类使用，不改变原有契约。
- **接口隔离（ISP）**：接口按调用方需要拆分，不强迫实现方依赖不需要的方法。
- **依赖倒置（DIP）**：依赖抽象（接口 / 端口），不依赖具体实现。Service 通过 Port 调用外部能力。
- **合成复用（CRP）**：优先组合而非继承，避免继承层次过深。
- **迪米特法则（LoD）**：只与直接朋友通信，不暴露内部细节给外部。

### 单业务单类

- 一个类只承担一个业务职责，只提供与其业务相关的接口方法。
- 判断标准：如果一个类的方法可以自然地分成两组、且两组之间互不调用，则应拆成两个类。
- **Agent 类特殊规则**：一个 Agent 类只负责构造一个 Agent。不同角色 / 不同用途的 Agent 必须拆成独立类，禁止在一个类中通过参数分支构造多种 Agent。
- Service 类同理：如果一个 Service 注入了多个不相关的 Repository 或 Port，说明它承担了过多职责，应拆分。

### 禁止重复代码

- 写新代码前，先搜索是否有可复用的现有实现（工具类、基类、通用 Service）。
- 两处以上出现相似逻辑时，必须抽取公共方法 / 泛型基类 / 策略模式，不允许复制粘贴。
- 相似但不完全相同的代码，优先考虑：泛型参数化 → 策略模式 → 模板方法 → 函数式参数。

### 抽象与高级技巧

- **泛型**：通用 CRUD、分页查询、事件处理等场景使用泛型基类（如 `BaseEntity<ID>`、`BaseService<T, ID>`）减少样板代码。
- **反射**：仅在框架层（如通用序列化、动态工具装配）使用，业务代码中优先用多态替代。
- **函数式接口**：回调、过滤、转换等场景优先用 `Function` / `Predicate` / `Consumer`，避免为一次性逻辑定义匿名类。
- **设计模式**：按需使用策略、工厂、观察者、模板方法等，但不过度设计——模式是手段不是目的。

## Conventions

### 禁止使用 Java record

不允许使用 `record` 类型。所有数据载体必须使用 `class`（可搭配 Lombok `@Data` / `@Value` 或手写 getter/setter）。现有代码中的 record 应在后续重构中逐步替换。

### 命名规则

- 全部使用驼峰命名（camelCase），包括 Java 代码、SQL 列名、JSON 字段
- JPA 实体列名使用 camelCase（已通过 `PhysicalNamingStrategyStandardImpl` 保证）
- Flyway 迁移 SQL 中的列名也必须使用 camelCase，禁止 snake_case
- 类名 PascalCase，方法名/变量名 lowerCamelCase，常量 UPPER_SNAKE_CASE
- **类名 + 字段名 / 类名 + 方法名组合后能表达完整语义即可**，字段名/方法名不必重复类名
  - 推荐：`question.text`、`questionService.ask()`、`projectSpaceService.list()`
  - 避免：`question.questionText`、`questionService.askQuestion()`、`projectSpaceService.listAllProjectSpaces()`
- Service 注入主聚合 Repository 时，字段命名为 `repository`（仅注入一个时）；辅助仓库才用全名
- 实体的状态转换方法用动词即可（`succeed()` / `fail()`），无需 `mark-` 前缀
- 局部变量按作用域长度命名：短作用域用缩写（`req`/`ctx`/`id`），跨方法/对外接口用全名
- 布尔字段不要带 `is/has` 前缀，留给 getter

### 分层架构

```
Controller → Service → Repository
                ↕
           Port（端口接口）→ Infrastructure（第三方实现）
```

- Controller 只做参数校验和响应转换，不写业务逻辑。
- Service 是业务逻辑的唯一归属，只注入 Repository 和 Port，不直接调用 Infrastructure。
- Repository 只做数据访问，不包含业务逻辑。
- 端口接口（Port）定义在业务模块内，Infrastructure 实现在独立包中，通过依赖倒置解耦。
- Service 之间通过领域事件交互，不互相注入（除非同属一个聚合）。

### 注释要求

- 每个类必须有类级 Javadoc 注释，说明该类的职责（做什么，不是怎么做）
- 非显而易见的方法应添加简短注释说明意图，尤其是：对外接口方法、复杂业务逻辑、非直觉的边界处理
- 不要为 getter/setter、简单赋值等自解释代码添加注释

### 日志要求

- 使用 Lombok `@Slf4j`（优先），或手写 SLF4J Logger
- 关键业务节点必须添加日志：方法入口（带关键参数）、外部调用前后（如 CodeGraph CLI 调用、LLM API 调用）、异常捕获点
- 日志级别：`DEBUG` 用于调试细节（CLI 命令输出、中间结果），`INFO` 用于关键业务事件（索引开始/完成、问题提交/回答完成），`WARN` 用于可恢复异常，`ERROR` 用于不可恢复异常
- 禁止使用 `System.out.println` 或 `e.printStackTrace()`

### 使用 Lombok 简化代码

- 项目已集成 Lombok，必须使用 Lombok 注解替代手写样板代码
- 数据载体类：使用 `@Data`（可变）或 `@Value`（不可变），替代手写 getter/setter/equals/hashCode/toString
- JPA 实体类：使用 `@Getter` `@Setter` `@NoArgsConstructor` `@AllArgsConstructor`，避免 `@Data`（JPA 实体需要受控的 equals/hashCode）
- 日志：使用 `@Slf4j` 替代手写 `LoggerFactory.getLogger()`
- 构造器：使用 `@NoArgsConstructor` / `@AllArgsConstructor` / `@RequiredArgsConstructor`
- Builder 模式：使用 `@Builder` 替代手写 Builder 类

### 异常处理

- Service 层只抛领域异常（`ResourceNotFoundException`、`InvalidStateException`、`DuplicateException`、`ValidationException`、`ToolExecutionException`），不依赖 HTTP 状态码
- 禁止在 Service 层抛 `ResponseStatusException`
- `GlobalExceptionHandler` 统一将领域异常转换为 HTTP 响应

### 事务与事件

- 跨聚合操作通过领域事件解耦，禁止在一个 Service 中直接操作另一个聚合的 Repository
- `@TransactionalEventListener` 方法不得同时标注 `@Transactional`（Spring 不允许）
- 异步线程中需要事务时，使用 `TransactionTemplate` 编程式事务，不依赖 `@Transactional`（ThreadLocal 不跨线程）
- 长耗时操作（如 Agent 调用）必须在事务外执行，避免长时间持有数据库连接

### 数据库

- MySQL 8.4，schema 仅通过 Flyway 管理（`ddl-auto: validate`）
- 列名 camelCase（`PhysicalNamingStrategyStandardImpl`）
- Flyway 迁移 SQL 中列名也必须使用 camelCase，禁止 snake_case

### API 规范

- RESTful 风格，URL 使用 kebab-case（`/api/project-spaces/{id}`）
- 请求参数使用 `@RequestBody` 接收 JSON，路径参数使用 `@PathVariable`
- 响应体统一包装为 `{ code, message, data }` 格式
- 分页请求使用 `page` + `size` 参数，响应包含 `total`、`content`
- SSE 流式端点使用 `text/event-stream`，事件格式遵循项目现有约定
- 错误响应由 `GlobalExceptionHandler` 统一生成，Controller 不直接构造错误响应

### 前端规范

- **组件**：按功能组织，一个 `.vue` 文件一个组件，文件名 PascalCase（`ProjectCard.vue`）
- **状态管理**：使用 Pinia，Store 文件放在 `src/stores/`，按业务域拆分（`project.ts`、`question.ts`）
- **路由**：集中定义在 `src/router/index.ts`，路由名使用 kebab-case
- **类型**：所有 API 响应、组件 Props 必须有 TypeScript 类型定义，放在 `src/types/`
- **API 调用**：统一通过 `src/services/` 封装，使用 `src/services/httpClient.ts` 作为 Axios 实例
- **样式**：使用 Scoped CSS，设计令牌定义在 `src/tokens.css`
- **禁止**：`any` 类型（除非有明确注释说明理由）、内联样式（除非仅用于动态计算值）

### 测试规范

- **后端**：JUnit 5 + Mockito，测试类名以 `Tests` 结尾，与被测类同包
- **前端**：Vitest + Vue Test Utils，测试文件以 `.test.ts` 结尾，放在同目录
- 核心业务逻辑（Service 层、领域模型）必须有单元测试
- 修改已有代码前，确保相关测试通过；新增功能需配套测试

### 安全

- 用户输入拼入命令时，必须通过 `SafePathValidator.sanitizeArg()` 消毒
- 文件路径必须通过 `SafePathValidator.validateUnderRoot()` 校验
- `RestrictedCommandTools` 白名单仅允许只读命令，新增命令需安全审查

### Git 提交规范

- Commit message 使用 Conventional Commits 格式：`type(scope): description`
- 常用 type：`feat`、`fix`、`refactor`、`test`、`docs`、`chore`
- 一个提交只做一件事，保持原子性
