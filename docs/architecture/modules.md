# Ascoder 模块边界

本文档描述 Ascoder 的逻辑模块边界（与 Maven 物理模块解耦，作为包内组织指引）。当前阶段保持单体应用形态，通过包边界、端口接口和事件隔离约束依赖方向。

## 包内组织原则

模块内部优先按"大功能 / 聚合能力"组织，不机械套用 `api`、`domain`、`application`、`persistence`、`web` 等技术分层包。

适合保留为独立内部包的，是未来有机会独立抽成模块包的能力边界，例如：

- `repository.git`：Git 操作能力。
- `repository.project`：项目与项目成员仓库管理。
- `repository.projectspace`：项目空间准备、索引与刷新。
- `repository.workspace`：分支 worktree 管理。
- `agent.extension.skill`：Skill 接入能力。
- `agent.extension.mcp`：MCP 接入能力。

只有当一个内部功能包继续膨胀到难以阅读，或确实需要独立对外契约时，才在该功能包内进一步拆分技术层。当前阶段宁可保持包结构粗一些，让模块边界先稳定。

## 模块划分

### common

基础能力模块，所有模块可以依赖它，它不依赖任何业务模块。

职责：

- 领域异常与全局异常处理（`GlobalExceptionHandler`）
- 异步任务框架（`TaskEngine` / `TaskDefinition` / `TaskContextSerializer`）
- 安全路径校验（`SafePathValidator`）
- 命令执行封装
- 文件与文本工具
- 通用配置和健康检查

当前主要包：

- `cn.welsione.ascoder.common`
- `cn.welsione.ascoder.common.exception`
- `cn.welsione.ascoder.common.security`
- `cn.welsione.ascoder.common.task`

### repository

代码资产与工作区模块，负责代码从哪里来、以什么版本被分析、落在什么本地路径。

职责：

- 仓库注册与状态管理
- Git clone / fetch / pull / branch / commit 操作
- Project 与 ProjectSpace 管理
- BranchWorkspace / worktree 管理
- 项目空间准备、刷新、删除
- Git 相关异步任务定义（clone / fetch / branch refresh）

当前主要包：

- `cn.welsione.ascoder.repository`
- `cn.welsione.ascoder.repository.git`
- `cn.welsione.ascoder.repository.project`
- `cn.welsione.ascoder.repository.projectspace`
- `cn.welsione.ascoder.repository.workspace`
- `cn.welsione.ascoder.repository.task`

边界要求：

- 对外优先暴露 Snapshot / Facade，不把 JPA Entity 作为长期跨模块契约。
- 跨聚合清理优先通过领域事件（如 `ProjectSpaceDeletedEvent`、`ProjectSpaceFetchCompletedEvent`），不直接操作其他模块 Repository。

### codegraph

CodeGraph CLI 接入模块，负责代码图谱的索引、同步与查询。独立于 `analysis`，提供端口接口供业务模块依赖。

职责：

- CodeGraph CLI 封装（索引 / 同步 / 查询 / 符号探索）
- 索引进度追踪（`IndexProgressTracker`）
- 索引与同步异步任务定义

当前主要包：

- `cn.welsione.ascoder.codegraph`
- `cn.welsione.ascoder.codegraph.port`（`CodeGraphClient` 端口接口）
- `cn.welsione.ascoder.codegraph.infrastructure`（CLI 实现）
- `cn.welsione.ascoder.codegraph.task`

边界要求：

- 业务模块依赖 `CodeGraphClient` 端口，不依赖 CLI 实现。
- CLI 实现细节（进程超时、输出解析）不泄漏到端口接口。

### analysis

代码分析工具模块，负责从仓库工作区提取结构化代码上下文，装配为 Agent 可调用的工具。

职责：

- 代码分析工具（`CodeAnalysisTools` / `FileInspectionTools` / `TextSearchTools`）
- 代码证据提取（`CodeEvidenceExtractor`）
- Git 来源信息工具（`GitProvenanceTools`）
- 安全命令工具（`RestrictedCommandTools`）
- CodeGraph 工具支持（`CodeGraphToolSupport`）

当前主要包：

- `cn.welsione.ascoder.analysis`（扁平包，按工具类组织）

边界要求：

- 依赖 `codegraph.port` 和 `repository` 提供的能力，不直接依赖 CLI 实现。
- 工具类通过 Agent 装配层注入，不自行管理生命周期。

### agent

Agent 能力模块，负责 Agent 构建、编排、提示词、工具装配、Skill 和 MCP 接入。

职责：

- Agent 请求与回答模型
- Agent 端口接口（`ChatModelFactory` 等）
- AgentScope 运行时适配
- Prompt 模板与角色风格管理
- 工具装配
- Skill 管理
- MCP 接入管理
- Agent 运行记录与监控

当前主要包：

- `cn.welsione.ascoder.agent`
- `cn.welsione.ascoder.agent.application`
- `cn.welsione.ascoder.agent.domain`
- `cn.welsione.ascoder.agent.port`
- `cn.welsione.ascoder.agent.persistence`
- `cn.welsione.ascoder.agent.web`
- `cn.welsione.ascoder.agent.extension`（skill / mcp 扩展）
- `cn.welsione.ascoder.agent.infrastructure`（AgentScope 适配，含 `agentscope` 子包）
- `backend/src/main/resources/prompts`

边界要求：

- 对外端口不得暴露 AgentScope、Anthropic 等具体运行时类型。
- 具体运行时只存在于 `agent.infrastructure.*`。
- Skill 与 MCP 暂作为 Agent 扩展接入层，暂不拆独立顶层模块。

### question（chat）

产品问答流程模块，负责用户问题、会话、流式输出和回答持久化。

职责：

- Question / Conversation 管理
- QuestionPlanner 与 QueryPlan
- AgentRequest 构建
- 回答写回
- SSE 流式事件推送
- 问答相关 Controller

当前主要包：

- `cn.welsione.ascoder.question.api`
- `cn.welsione.ascoder.question.application`
- `cn.welsione.ascoder.question.domain`
- `cn.welsione.ascoder.question.persistence`
- `cn.welsione.ascoder.question.planning`
- `cn.welsione.ascoder.question.stream`
- `cn.welsione.ascoder.question.web`

边界要求：

- 只依赖 Agent 自有端口和领域模型，不依赖 AgentScope 运行时类型。
- 调用 Agent 的长耗时过程必须在事务外执行。

### selflearning

自学习模块，负责从问答历史中提取术语、知识、经验和洞察，供 Agent 后续问答复用。

职责：

- LearningRawEvent 采集与导入
- 术语 / 知识项 / 经验 / 纠正管理
- 自学习 Agent 运行编排（`SelfLearningAgentRunScheduler`）
- 洞察生成与审核
- 自学习设置管理

当前主要包：

- `cn.welsione.ascoder.selflearning`（扁平包，按聚合根组织）

边界要求：

- 依赖 `question`（问答历史）和 `agent`（自学习 Agent），不反向被依赖。
- 自学习异常（`SelfLearningInsightException`）继承 `DomainException`，由模块内 `SelfLearningExceptionHandler` 统一映射，不污染 `common` 的全局处理器。

### loganalysis

日志分析模块，负责解析用户上传的运行日志，提取结构化信息辅助问答。

职责：

- 日志解析与结构化
- 日志模式识别
- 日志分析结果管理

当前主要包：

- `cn.welsione.ascoder.loganalysis`
- `cn.welsione.ascoder.loganalysis.application`
- `cn.welsione.ascoder.loganalysis.domain`
- `cn.welsione.ascoder.loganalysis.persistence`
- `cn.welsione.ascoder.loganalysis.web`

### runtime

运行时设置模块，负责管理可在运行时调整的配置项（如 LLM 供应商连接、Agent 参数等）。

职责：

- 运行时配置 CRUD
- LLM 供应商连接测试
- 配置变更通知

当前主要包：

- `cn.welsione.ascoder.runtime`
- `cn.welsione.ascoder.runtime.application`
- `cn.welsione.ascoder.runtime.domain`
- `cn.welsione.ascoder.runtime.persistence`
- `cn.welsione.ascoder.runtime.web`

### config

应用配置模块，负责启动配置绑定和属性类定义。

职责：

- `AscoderProperties` 配置属性绑定
- `PropertiesConfiguration` 配置装配

当前主要包：

- `cn.welsione.ascoder.config`

### app

应用组装模块，负责启动、配置绑定和最终运行时装配。

当前主要内容：

- `cn.welsione.ascoder.AscoderApplication`
- `application.yml` / `application-dev.yml`
- Flyway migration
- Docker / compose 配置

## 依赖方向

推荐依赖方向：

```text
app
  -> question (chat)
  -> agent
  -> selflearning
  -> loganalysis
  -> runtime
  -> codegraph
  -> analysis
  -> repository
  -> common

question
  -> agent
  -> repository
  -> loganalysis
  -> common

selflearning
  -> question
  -> agent
  -> common

agent
  -> analysis
  -> codegraph
  -> common

analysis
  -> codegraph
  -> repository
  -> common

codegraph
  -> common

repository
  -> common

runtime
  -> common
```

阶段性允许 `repository -> codegraph.task` 用于项目空间索引编排；跨聚合操作通过领域事件解耦。

## 重构优先级

1. 隔离 Agent 对外端口中的第三方运行时类型。
2. 将 AgentScope 工具类逐步拆成 `analysis` 能力和 `agent.tool` 装配能力。
3. 为 repository 模块提供 ProjectSpace / Workspace Snapshot，减少跨模块传递 JPA Entity。
4. 把 Skill 与 MCP 移入 Agent 扩展层的包结构。
5. 评估 `selflearning` 是否需要拆分技术层包（当前扁平包已较大）。
