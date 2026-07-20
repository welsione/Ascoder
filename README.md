# Ascoder

团队代码理解平台 — 对代码仓库进行结构化问答、索引与分析。

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

### Docker Compose 启动

```bash
cp .env.example .env
docker compose up -d
```

## 文档

- [部署指南](DEPLOY.md) — Docker Compose 配置、私有仓库访问、Windows + WSL2 部署
- [架构模块](docs/architecture/modules.md) — 后端模块划分与依赖方向

## 技术栈

- **后端**：Java 17 / Spring Boot 3.3 / JPA / Flyway / MySQL 8.4
- **前端**：Vue 3 / TypeScript / Pinia / Element Plus / Vite
- **分析**：CodeGraph CLI（代码图谱）+ Agent（LLM 工具调用）
- **部署**：Docker Compose

## 许可证

私有项目
