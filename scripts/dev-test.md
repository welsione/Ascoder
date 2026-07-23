# 测试环境指南

Ascoder 提供两种本地测试模式,共用一套 docker 化测试数据库(MySQL 8.4,宿主机端口 **3307**,数据持久化到 `./data/mysql-test`)。

| 模式 | 后端 | 前端 | 数据库 | 适用场景 |
|---|---|---|---|---|
| `dev-local` | 本地 IDE / mvn | 本地 `npm run dev` | docker 容器(3307) | 日常调试,断点、热重载 |
| `dev-docker` | 容器 | 容器 | 容器(内部网络) | 验证 docker 部署链路、发布前 smoke test |

两种模式互不干扰,且与生产部署(`docker-compose.prod.yml`)、本地开发(`application-local.yml` 连 3306)完全隔离。

## 前置条件

- Docker Engine + docker compose v2
- JDK 17、Maven(`dev-local` 需要本地跑后端)
- Node 22(`dev-local` 需要本地跑前端)
- 端口 3307(测试数据库)、18080(后端,`dev-docker` 暴露)、5173(前端,`dev-docker` 暴露)空闲

## 一键脚本

```bash
bash scripts/dev.sh local up       # 起测试数据库,后端/前端本地跑
bash scripts/dev.sh local down     # 停数据库(保留数据)
bash scripts/dev.sh local reset    # 停数据库并清空数据

bash scripts/dev.sh docker up      # 全套容器
bash scripts/dev.sh docker down    # 停全部(保留数据)
bash scripts/dev.sh docker reset   # 停全部并清空数据
bash scripts/dev.sh docker logs    # 查看后端日志
```

## dev-local:本地启动 + 容器数据库

数据库跑在容器里,后端/前端跑在本地,兼顾调试便利与数据隔离。

```bash
# 1. 起测试数据库(3307)
bash scripts/dev.sh local up

# 2. 启动后端(激活 dev-local profile,连接 127.0.0.1:3307)
mvn spring-boot:run -pl backend -Dspring-boot.run.profiles=dev-local
#   或在 IDE 中设置 Active profiles: dev-local

# 3. 启动前端(vite proxy 已将 /api 指向 localhost:18080)
cd frontend/web && npm run dev
```

- 后端: http://localhost:18080/api/health
- 前端: http://localhost:5173

> `dev-local` profile 默认 `llm-provider=database`,不依赖任何 LLM API Key。需要问答功能时,在「设置 → 模型供应商」页面手动添加 provider。

## dev-docker:全套容器

整套应用跑在 docker compose 中,数据库通过内部网络(`mysql:3306`)与后端通信。

```bash
bash scripts/dev.sh docker up
```

脚本会先 `mvn package` 打包后端 jar(因 `backend/Dockerfile` COPY 预构建 jar),再 `docker compose --profile docker up -d --build` 启动 backend + frontend + mysql。

- 前端: http://localhost:5173
- 后端: http://localhost:18080/api/health

## 数据库连接信息

| 项 | 默认值 | 环境变量覆盖 |
|---|---|---|
| 宿主机端口 | 3307 | `TEST_MYSQL_PORT` |
| 数据库名 | ascoder | `TEST_MYSQL_DATABASE` |
| 用户名 | ascoder | `TEST_MYSQL_USER` |
| 密码 | test-password | `TEST_MYSQL_PASSWORD` |
| root 密码 | test-password | `TEST_MYSQL_ROOT_PASSWORD` |

> 这些默认值仅用于本地测试,不要用于生产。覆盖时 `docker-compose.test.yml` 与 `application-dev-local.yml` 须保持一致(`dev-local` 通过同名 `TEST_MYSQL_*` 环境变量读取)。

## 数据清理

测试数据持久化在 `./data/mysql-test`(bind mount,已被 `.gitignore` 忽略)。

- `down`:停止容器,数据保留。
- `reset`:停止容器并删除 `./data/mysql-test`,下次 `up` 时 MySQL 重新初始化、Flyway 重建全部表。

```bash
bash scripts/dev.sh local reset    # 或 docker reset
```

## 加密密钥

`dev-local` / `dev-docker` 已加入 `SecurityConfiguration.DEV_PROFILES`,允许在未配置 `ASCODER_ENCRYPTION_KEY` 时使用开发默认密钥。因此测试环境无需配置加密密钥即可使用「设置 → 模型供应商」功能。

> 生产环境仍必须显式配置 `ASCODER_ENCRYPTION_KEY`(由 `install.sh` / `deploy.sh` 自动生成)。

## 常见问题

**端口 3307 被占用**:设 `TEST_MYSQL_PORT=3308` 后 `up`;`dev-local` 后端会通过同名变量连接。`dev-docker` 模式下后端走容器内部网络,不受宿主机端口影响。

**`dev-docker up` 报 jar 不存在**:`backend/Dockerfile` 依赖 `backend/target/ascoder-backend-0.1.0-SNAPSHOT.jar`。脚本已自动执行 `mvn package`;若手动 `docker compose build`,需先 `mvn -pl backend -am package -DskipTests`。

**Flyway 报 schema 不一致**:`reset` 清空数据后重新 `up`,让 Flyway 从 V1 顺序迁移。
