# Ascoder 开发指南

本文档面向开发者，介绍本地环境搭建、开发流程与测试约定。

## 前置条件

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | 后端编译运行 |
| Node.js | 20+ | 前端构建（Docker 镜像用 22） |
| MySQL | 8.4 | 数据库（可用 Docker 容器化） |
| CodeGraph CLI | 1.1+ | 代码图谱分析工具 |
| Maven | 3.9+ | 后端构建（或用 `./mvnw`） |
| Docker | 24+ | 容器化测试环境（可选但推荐） |

### 安装 CodeGraph CLI

```bash
npm install -g @colbymchenry/codegraph
codegraph --version  # 验证安装
```

## 快速启动（dev-local 模式）

推荐的开发模式：后端和前端在本地运行，MySQL 用 Docker 容器化（端口 3307，避免与宿主机 3306 冲突）。

### 1. 启动测试数据库

```bash
docker compose -f docker-compose.test.yml up -d mysql
```

数据库监听 `localhost:3307`，测试账号 `ascoder / test-password`，数据持久化到 `./data/mysql-test`。

### 2. 启动后端

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev-local
```

后端启动在 `http://localhost:18080`，`dev-local` profile 连接 3307 端口的测试数据库。

### 3. 启动前端

```bash
cd frontend/web
npm install
npm run dev
```

前端开发服务器在 `http://localhost:5173`，`/api` 请求自动代理到 `http://localhost:18080`。

### 4. 一键脚本（可选）

```bash
bash scripts/dev.sh dev-local up    # 启动测试数据库
bash scripts/dev.sh dev-local down  # 停止
```

## 全容器模式（dev-docker）

如需全套容器化开发（后端也跑在容器内，支持热重建）：

```bash
bash scripts/dev.sh dev-docker up
```

详见 `scripts/dev.sh` 与 `docker-compose.test.yml`。

## 配置说明

### 后端 Profile

| Profile | 用途 | 数据库 |
| --- | --- | --- |
| `dev-local` | 本地 IDE / `mvn spring-boot:run` | Docker 化 MySQL（localhost:3307） |
| `dev-docker` | 全容器开发 | Docker Compose 内 MySQL |
| 默认（无 profile） | 生产 / Docker Compose 部署 | `host.docker.internal:3306` 或环境变量指定 |

### 关键配置项（`application.yml`）

- `SPRING_DATASOURCE_URL`：数据库连接串
- `SPRING_DATASOURCE_USERNAME` / `PASSWORD`：数据库凭据
- `ascoder.repo-root`：仓库克隆根目录
- `ascoder.worktree-root`：worktree 根目录
- `ascoder.project-space-root`：项目空间根目录
- `codegraph.executable`：CodeGraph CLI 路径（默认 `codegraph`，需在 PATH 中）

### 前端环境变量

前端通过 Vite 构建，API 基地址由 `VITE_API_BASE_URL` 控制：
- 开发模式：留空，Vite proxy 代理 `/api` 到后端
- 生产模式（Docker）：留空，nginx 反向代理 `/api`

## 测试

### 后端测试

```bash
cd backend

# 运行全部测试
./mvnw test

# 运行单个测试类
./mvnw test -Dtest=GitFetchTaskDefinitionTests

# 运行单个测试方法
./mvnw test -Dtest="GitFetchTaskDefinitionTests#executeFetchOperationCallsFetch"
```

测试约定：
- 测试类名以 `Tests` 结尾（如 `RepositoryServiceTests`），与被测类同包
- 单元测试用 JUnit 5 + Mockito，不依赖 Spring 上下文
- 集成测试用 `@SpringBootTest` + Testcontainers 或 Docker 化 MySQL
- 测试目录结构与源码一致：`src/test/java/cn/welsione/ascoder/...`

### 前端测试

```bash
cd frontend/web

npm run test          # 运行全部测试（vitest run）
npm run test:watch    # 监听模式
```

测试约定：
- 测试文件以 `.test.ts` 结尾，放在被测文件同目录
- 使用 Vitest + Vue Test Utils
- Store / composable 测试用 `vi.fn()` mock API 调用

## 代码规范

详见 [CLAUDE.md](../CLAUDE.md)，核心要点：

- **后端**：Java 17，禁止 `record`，优先 Lombok `@Data` / `@Value`，Service 只抛领域异常，跨聚合用领域事件解耦
- **前端**：Vue 3 + TypeScript，禁止 `any`，组件 PascalCase，Store 按业务域拆分
- **命名**：驼峰，方法名不超过 25 字符，类名+方法名组合表达完整语义
- **提交**：Conventional Commits（`type(scope): description`），原子提交

## IDE 配置建议

### IntelliJ IDEA

- 安装 Lombok 插件（Settings -> Plugins -> Lombok）
- 启用注解处理器（Settings -> Build -> Compiler -> Annotation Processors -> Enable）
- 导入为 Maven 项目，选择 `backend/pom.xml`
- 推荐插件：SonarLint、GitToolBox

### VS Code

- 安装扩展：Volar（Vue）、Java Extension Pack、Lombok Annotations Support
- 前端工作区：`frontend/web`
- 后端工作区：`backend`

## 常见问题

### 后端启动报 CodeGraph CLI 找不到

确认 `codegraph` 在 PATH 中：

```bash
which codegraph
# 若不在 PATH，在 application-dev-local.yml 中指定绝对路径：
# codegraph.executable: /usr/local/bin/codegraph
```

### 后端启动报数据库连接失败

```bash
# 确认测试数据库在运行
docker compose -f docker-compose.test.yml ps

# 查看数据库日志
docker compose -f docker-compose.test.yml logs mysql

# 重启数据库
docker compose -f docker-compose.test.yml restart mysql
```

### Flyway 迁移失败

- `ddl-auto: validate` 模式下，实体与表结构不匹配会启动失败
- 检查 Flyway 迁移脚本（`backend/src/main/resources/db/migration/`）是否全部执行
- 测试环境可重建数据库：`docker compose -f docker-compose.test.yml down -v && docker compose -f docker-compose.test.yml up -d mysql`

### 前端 `npm run dev` 端口被占用

Vite 默认用 5173，若被占用可在 `vite.config.ts` 的 `server.port` 修改，或：

```bash
npm run dev -- --port 5174
```

### Git worktree 创建失败

项目空间准备时会创建 worktree 和符号链接：
- **macOS / Linux**：用 `symlink`，需确保进程有权限
- **Windows**：用 `mklink /J`（junction），无需管理员权限；若失败检查目录是否被占用
- 检查 `ascoder.worktree-root` 指向的目录是否有写权限
