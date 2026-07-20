# Ascoder 模块边界

本文档描述 Ascoder v1 的第一版能力模块边界。当前阶段先保持单体应用形态，通过包边界、端口接口和事件隔离约束依赖方向；边界稳定后再拆 Maven module。

## 包内组织原则

模块内部优先按“大功能 / 聚合能力”组织，不机械套用 `api`、`domain`、`application`、`persistence`、`web` 等技术分层包。

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

- 领域异常与全局异常处理
- 安全路径校验
- 命令执行封装
- 文件与文本工具
- 通用配置和健康检查

当前主要包：

- `cn.welsione.ascoder.common`
- `cn.welsione.ascoder.common.exception`

### repository

代码资产与工作区模块，负责代码从哪里来、以什么版本被分析、落在什么本地路径。

职责：

- 仓库注册与状态管理
- Git clone / fetch / pull / branch / commit 操作
- Project 与 ProjectSpace 管理
- BranchWorkspace / worktree 管理
- 项目空间准备、刷新、删除

当前主要包：

- `cn.welsione.ascoder.repository`
- `cn.welsione.ascoder.repository.git`
- `cn.welsione.ascoder.repository.project`
- `cn.welsione.ascoder.repository.projectspace`
- `cn.welsione.ascoder.repository.workspace`

边界要求：

- 对外优先暴露 Snapshot / Facade，不把 JPA Entity 作为长期跨模块契约。
- 跨聚合清理优先通过领域事件，不直接操作其他模块 Repository。

### analysis

代码分析模块，负责从仓库工作区提取结构化代码上下文。

职责：

- CodeGraph 接入
- 索引与同步
- 符号查询、调用关系、影响分析
- 文件检查、文本检索、安全命令分析
- 代码证据提取

当前主要包：

- `cn.welsione.ascoder.codegraph`
- `cn.welsione.ascoder.analysis.infrastructure.agentscope`

边界要求：

- 业务模块依赖 `CodeGraphClient` 等端口，不依赖 CLI 实现。
- 工具能力应逐步从 AgentScope 适配层迁移为分析模块的通用能力。

### agent

Agent 能力模块，负责 Agent 构建、编排、提示词、工具装配、Skill 和 MCP 接入。

职责：

- Agent 请求与回答模型
- Agent 端口接口
- AgentScope 运行时适配
- Prompt 模板与角色风格管理
- 工具装配
- Skill 管理
- MCP 接入管理

当前主要包：

- `cn.welsione.ascoder.agent`
- `cn.welsione.ascoder.agent.extension.skill`
- `cn.welsione.ascoder.agent.extension.mcp`
- `backend/src/main/resources/prompts`

边界要求：

- 对外端口不得暴露 AgentScope、Anthropic 等具体运行时类型。
- 具体运行时只存在于 `agent.infrastructure.*`。
- Skill 与 MCP 暂作为 Agent 扩展接入层，暂不拆独立顶层模块。

### chat

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
- `cn.welsione.ascoder.question.domain`
- `cn.welsione.ascoder.question.persistence`
- `cn.welsione.ascoder.question.planning`
- `cn.welsione.ascoder.question.application`
- `cn.welsione.ascoder.question.stream`
- `cn.welsione.ascoder.question.web`

边界要求：

- 只依赖 Agent 自有端口和领域模型，不依赖 AgentScope 运行时类型。
- 调用 Agent 的长耗时过程必须在事务外执行。

### app

应用组装模块，负责启动、配置绑定和最终运行时装配。

当前主要内容：

- `cn.welsione.ascoder.AscoderApplication`
- `application.yml`
- Flyway migration
- Docker / compose 配置

## 依赖方向

推荐依赖方向：

```text
app
  -> chat
  -> agent
  -> analysis
  -> repository
  -> common

chat
  -> agent
  -> repository
  -> common

agent
  -> analysis
  -> common

analysis
  -> repository
  -> common

repository
  -> common
```

阶段性允许 `repository -> analysis` 用于项目空间索引编排；后续可通过 `analysis-api` 或 `CodeIndexPort` 反转依赖。

## 重构优先级

1. 隔离 Agent 对外端口中的第三方运行时类型。
2. 将 AgentScope 工具类逐步拆成 `analysis` 能力和 `agent.tool` 装配能力。
3. 为 repository 模块提供 ProjectSpace / Workspace Snapshot，减少跨模块传递 JPA Entity。
4. 把 Skill 与 MCP 移入 Agent 扩展层的包结构。
5. 在边界稳定后拆分 Maven module。
