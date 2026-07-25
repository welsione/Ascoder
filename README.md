# Ascoder

团队代码理解平台 - 对代码仓库进行结构化问答、索引与分析。

## 功能概览

- **仓库接入**：支持远程 Git 仓库 clone 与本地仓库接入，自动管理分支与凭据
- **项目空间**：组合多个仓库的特定分支，创建可独立索引与分析的代码空间
- **代码图谱**：基于 CodeGraph CLI 构建代码符号、调用关系与影响范围索引
- **智能问答**：LLM Agent 驱动的自然语言问答，支持多角色协作与流式输出
- **自学习**：从问答历史中提取术语、知识与经验，持续优化回答质量
- **异步任务**：内置异步任务框架，支持索引、clone、fetch 等长耗时操作与进度追踪

## 技术栈

- **后端**：Java 17 / Spring Boot 3.3 / JPA / Flyway / MySQL 8.4 / Maven
- **前端**：Vue 3 / TypeScript / Pinia / Vue Router / Element Plus / Vite
- **分析**：CodeGraph CLI（代码图谱）+ Agent（LLM 工具调用）
- **部署**：Docker Compose（Linux / Windows / macOS）

## 快速开始

### 前置条件

- [Docker](https://docs.docker.com/get-docker/) + Docker Compose v2
- （可选）LLM API Key（MiniMax / Anthropic / OpenAI 任一）

### 一键启动

```bash
cp .env.example .env
# 编辑 .env，填写 LLM API Key
docker compose up -d --build
```

启动后访问：http://localhost:5173

## 文档

| 文档 | 内容 |
| --- | --- |
| [开发指南](DEVELOPMENT.md) | 本地环境搭建、dev profile、测试约定、IDE 配置 |
| [部署指南](DEPLOY.md) | Docker Compose 配置、私有仓库访问、Windows + WSL2 部署 |
| [局域网部署](DEPLOY-LAN.md) | GitHub Actions + GHCR + cron/计划任务自动更新（含 Windows PowerShell 脚本） |
| [模块边界](docs/architecture/modules.md) | 逻辑模块划分、包结构、依赖方向 |
| [项目规范](CLAUDE.md) | 编码规范、命名规则、分层架构、设计原则 |

## 本地开发

详见 [开发指南](DEVELOPMENT.md)，快速开始：

```bash
# 1. 启动测试数据库
docker compose -f docker-compose.test.yml up -d mysql

# 2. 启动后端（dev-local profile）
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev-local

# 3. 启动前端
cd frontend/web && npm install && npm run dev
```

## 项目结构

```
Ascoder/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/cn/welsione/ascoder/
│   │   ├── common/          # 异常、安全、异步任务框架
│   │   ├── repository/      # 仓库、项目空间、worktree 管理
│   │   ├── codegraph/       # CodeGraph CLI 接入
│   │   ├── analysis/        # 代码分析工具
│   │   ├── agent/           # Agent 构建、编排、Skill/MCP
│   │   ├── question/        # 问答、会话、SSE 流式
│   │   ├── selflearning/    # 自学习
│   │   ├── loganalysis/     # 日志分析
│   │   ├── runtime/         # 运行时设置
│   │   └── config/          # 配置绑定
│   └── src/main/resources/
│       ├── db/migration/    # Flyway 迁移脚本
│       └── prompts/         # Agent 提示词模板
├── frontend/web/            # Vue 3 前端
│   └── src/
│       ├── stores/          # Pinia 状态管理
│       ├── services/        # API 封装
│       ├── composables/     # 组合式函数
│       ├── components/      # 组件
│       └── views/           # 页面
├── docker-compose.yml       # 本地开发
├── docker-compose.prod.yml  # 生产部署（GHCR 镜像）
├── docker-compose.test.yml  # 测试环境
└── scripts/server/          # 部署脚本（bash + PowerShell）
```

## 许可证

私有项目
