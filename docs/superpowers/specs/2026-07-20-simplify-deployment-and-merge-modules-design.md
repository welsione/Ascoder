# Ascoder 简化部署与合并 Maven 模块设计

> 日期：2026-07-20
> 状态：待用户 review
> 范围：合并 `ascoder-common` / `ascoder-codegraph` 两个 Maven 模块到 `backend`；删除非 Docker 部署方式（installer 一键引导、scripts/package、scripts/deploy）

## 背景与动机

Ascoder v1 当前以 Docker Compose 为唯一推荐部署方式，但仓库内仍保留多套"非 Docker"部署资产：

1. **物理 Maven 模块拆分过细**：`ascoder-common`（17 个 java）、`ascoder-codegraph`（6 个 java）作为独立 jar 存在，但代码体量小、依赖方向单向（`backend → codegraph → common`），拆分收益不抵构建与跳转成本。
2. **存在多套部署引导**：`installer/` 提供 install/uninstall/start/stop 脚本（内部包了 `docker compose`），`scripts/package.sh` 打包成 zip 给 installer 用，`scripts/deploy.{sh,ps1}` 封装 `docker compose up`。
3. **CLAUDE.md 重构优先级 #5**"在边界稳定后拆分 Maven module"与"代码极简"目标方向相反。

本次重构：**采用单体 Spring Boot + 物理 Maven 模块最小化**（仅保留 `jprompt` 独立 + `backend` 一体），同步删除面向终端用户的一键安装体验，让所有部署引导指向 `docker compose`。

## 目标

- 消灭物理 Maven 模块 `ascoder-common`、`ascoder-codegraph`
- 消灭 installer/、scripts/package.sh、scripts/deploy.{sh,ps1}
- 文档（README、DEPLOY、CLAUDE.md、modules.md）改写为纯 Docker 部署叙事
- 不重写 git 历史 / 不再次 squash（master 在前次 squash 单 commit 之上叠 1 个新 commit）

## 非目标

- 不重命名 Java 包路径（`cn.welsione.ascoder.common.*`、`cn.welsione.ascoder.codegraph.*` 保留）
- 不重写业务代码、无重构、无 API 调整
- 不动 `jprompt`（独立 git submodule）
- 不动前端代码、Dockerfile、docker-compose.yml 主体结构
- 不动数据库 / Flyway 迁移
- 不动 CLAUDE.md 描述的 6 个逻辑模块（common / repository / analysis / agent / chat / app）—— 它们作为包内组织指引保留，与 Maven 物理拆分解耦

## 范围变更详表

### 物理结构：ascoder-common / ascoder-codegraph → backend

| 现状 | 变更后 |
|---|---|
| `ascoder-common/src/main/java/cn/welsione/ascoder/common/**` (17 个 java) | `backend/src/main/java/cn/welsione/ascoder/common/**` |
| `ascoder-common/src/main/resources/`（无内容，跳过） | — |
| `ascoder-common/src/test/java/cn/welsione/ascoder/common/security/ApiKeyEncryptorTests.java` | `backend/src/test/java/cn/welsione/ascoder/common/security/ApiKeyEncryptorTests.java` |
| `ascoder-codegraph/src/main/java/cn/welsione/ascoder/codegraph/**` (6 个 java) | `backend/src/main/java/cn/welsione/ascoder/codegraph/**` |
| `ascoder-codegraph/src/test/java/.../CodeGraphCommandRunnerTests.java` | `backend/src/test/java/cn/welsione/ascoder/codegraph/infrastructure/cli/CodeGraphCommandRunnerTests.java` |
| `ascoder-common/pom.xml` | 删除 |
| `ascoder-codegraph/pom.xml` | 删除 |
| `ascoder-common/` 目录 | 删除 |
| `ascoder-codegraph/` 目录 | 删除 |

合并后 `backend/src/main/java/cn/welsione/ascoder/` 下顶层子包：

- 已存在：`common/`（合并）、`codegraph/`（合并）、`analysis/`、`repository/`、`agent/`、`question/`、`selflearning/`、`loganalysis/`
- `codegraph` 与 `analysis` 并列，符合 `modules.md` 描述的"分析模块"内部分包关系

### 依赖与构建

**根 `pom.xml`**：

- `<modules>` 节点移除 `ascoder-common`、`ascoder-codegraph`，仅保留 `jprompt`、`backend`
- 保留 `<groupId>cn.welsione</groupId>`、`<artifactId>ascoder</artifactId>`、`<version>0.1.0-SNAPSHOT</version>`、`<packaging>pom</packaging>`

**`backend/pom.xml`** 吸收两个被删 pom 的依赖：

- 新增显式依赖：`jackson-databind:2.17.0`、`slf4j-api:2.0.13`、`assertj-core:3.26.3`（`junit-jupiter:5.11.0` 通过 `spring-boot-starter-test` 传递，**不显式添加**以保持原有作用域）
- 删除依赖：`<dependency>ascoder-common</dependency>`、`<dependency>ascoder-codegraph</dependency>`
- 保留所有现有依赖：Spring Boot 全家桶、AgentScope、Anthropic、MapStruct、Flyway、MySQL、`jprompt`
- 保留 `spring-boot-maven-plugin`（让 backend 仍是 Spring Boot fat jar）
- 保留 `maven-compiler-plugin` 的 annotation processor（Lombok、MapStruct、Lombok-MapStruct binding）
- Lombok 的 scope 保持 backend 当前值（`optional`，Spring Boot starter parent 默认）不变

**`scripts/package.sh`**：删除（产给 installer 用的 zip，无下游消费者）

**`scripts/deploy.sh` / `deploy.ps1`**：删除（便捷 `docker compose up` 封装，与"用户直接用 `docker compose`"的目标冲突）

**`scripts/verify-docker.sh`**：保留

**`Dockerfile`（`backend/Dockerfile`、`frontend/web/Dockerfile`）**：不变

**`docker-compose.yml`**：不变

### 删除非 Docker 部署

| 文件/目录 | 处理 |
|---|---|
| `installer/` 全部（install.sh/ps1、uninstall.sh/ps1、bin/start.sh/ps1、bin/stop.sh/sh、conf/application.yml、README.txt） | **删除** |
| `installer/.DS_Store` | **删除**（随目录） |
| `scripts/package.sh` | **删除** |
| `scripts/deploy.sh` | **删除** |
| `scripts/deploy.ps1` | **删除** |
| `scripts/verify-docker.sh` | **保留** |
| `docs/superpowers/specs/2026-06-16-installer-tui-design.md` | **删除**（installer TUI 历史设计） |
| `docs/superpowers/plans/2026-06-16-installer-tui.md` | **删除**（installer TUI 实施计划） |
| `build/` 目录（含 `output/`） | **保留**（被 `.gitignore` 忽略，不是跟踪项） |

### 文档清理

**`README.md`**：

- "快速安装"段中所有 `./installer/install.sh`、`.\installer\install.ps1` 引用 → 改为 `cp .env.example .env && docker compose up -d` 流程
- 删去 `installer/` 相关的所有引用

**`DEPLOY.md`**：

- 第 192 行（PowerShell ExecutionPolicy）→ 删除（不再相关）
- 第 196 行（`install.sh` 注册 launchd/systemd）→ 删除
- 第 198–206 行（"升级指引"整段，从 install.sh + java -jar 迁移到 Docker Compose）→ 删除
- 通篇扫一遍，确保无 `install.sh` / `install.ps1` / `uninstall` / `java -jar` 残留

**`CLAUDE.md`**：

- "项目全景"段保留（已是"构建：Maven（backend），Vite（frontend），Docker Compose（部署）"）
- "模块边界"段：保留 6 个逻辑模块描述
- **重写"重构优先级"第 5 条**：从"在边界稳定后拆分 Maven module"改为"当前采用单体 Spring Boot + 物理 Maven 模块最小化（`jprompt` 独立 + `backend` 一体），如需进一步拆分再评估"
- 新增一句"物理 Maven 模块见根 pom.xml"指向 `pom.xml` 的 modules 节点

**`docs/architecture/modules.md`**：

- 顶部"本文档描述 Ascoder v1 的第一版能力模块边界。当前阶段先保持单体应用形态，通过包边界、端口接口和事件隔离约束依赖方向；边界稳定后再拆 Maven module" → 改为"本文档描述 Ascoder 的逻辑模块边界（与 Maven 物理模块解耦，作为包内组织指引）"
- "重构优先级"段删除"在边界稳定后拆分 Maven module"（已被 CLAUDE.md 取代）

**其他文档**：检查 `docs/superpowers/{specs,plans}/` 下其他文件是否有 installer / install.sh 引用，逐一清理

### Commit 策略

合成 1 个 commit（不是 2 个）。理由：两个变更在代码层耦合（CLAUDE.md / modules.md 改动同时涉及"合并模块后逻辑模块的描述调整"与"删除 installer 后的部署文档清理"，分开反而留下中间状态）。commit message 见下。

## 提交信息

```
refactor: 合并 ascoder-common/ascoder-codegraph 到 backend,删除非 Docker 部署

- 物理 Maven 模块从 4 个(jprompt/ascoder-common/ascoder-codegraph/backend)缩减为 2 个(jprompt/backend)
- ascoder-common 与 ascoder-codegraph 的源代码移入 backend,保留原包路径
- backend pom 合并吸收 jackson-databind/slf4j-api/assertj-core 依赖
- 根 pom modules 移除 ascoder-common 与 ascoder-codegraph
- 删除 installer/ 目录及脚本
- 删除 scripts/{package,deploy}.{sh,ps1},保留 verify-docker.sh
- 删除 docs/superpowers/{specs,plans}/2026-06-16-installer-tui-*.md
- DEPLOY.md / README.md 移除 installer 引导,改为纯 docker compose 流程
- CLAUDE.md 模块边界重写"在边界稳定后拆分"段,docs/architecture/modules.md 同步
- 不重写 git 历史 / 不再次 squash,master 在前次 squash 单 commit 之上叠 1 commit
```

## 验证清单

1. `mvn -pl backend -am clean package` 构建成功
2. `mvn -pl backend -am test` 所有测试通过（含迁移过来的 `ApiKeyEncryptorTests`、`CodeGraphCommandRunnerTests`）
3. `git ls-files | grep -E '^installer/|^ascoder-common/|^ascoder-codegraph/'` 应无输出
4. `git ls-files | grep -E '^scripts/(package|deploy)'` 应无输出
5. `grep -rE 'install\.sh|install\.ps1|uninstall\.(sh|ps1)|scripts/package\.sh|scripts/deploy\.' --include='*.md' --include='*.yml' --include='*.sh' --include='*.json' .` 应无命中
6. `docker compose config` 语法校验通过
7. `grep -rE 'ascoder-common|ascoder-codegraph' --include='pom.xml'` 应无命中
8. 走代理 `git push origin master` 成功

## 风险与边界

- **破坏现有依赖外部用户**：若有人正在引用 `cn.welsione.ascoder:ascoder-common` 或 `ascoder-codegraph` 作为外部依赖（maven 坐标），合并后该坐标消失。本次仓库是首次公开开源（master 是新 squash commit），无外部消费者，风险为 0。
- **jprompt 仍为独立 submodule**：根 pom 仍保留 `<modules>` 节点（含 jprompt + backend）。完全单 module 需要进一步处理 jprompt，超出本次范围。
- **install.sh 引导过的旧用户**：DEPLOY.md 删除"升级指引"段后，旧 install.sh 用户升级路径不写明。如社区反馈需要，再补一个迁移 FAQ。
- **DEPLOY.md 行号在调整后可能变化**：清理时按内容定位，不依赖行号。

## 实施步骤（高层）

1. `git mv` 移动 `ascoder-common/src/...` 与 `ascoder-codegraph/src/...` 到 `backend/src/...`
2. 删除 `ascoder-common/pom.xml`、`ascoder-codegraph/pom.xml` 与两个目录
3. 修改根 `pom.xml` 的 `<modules>` 节点
4. 修改 `backend/pom.xml`：删除两行 dependency，新增三行 dependency
5. 删除 `installer/`、`scripts/{package.sh,deploy.sh,deploy.ps1}`
6. 删除 `docs/superpowers/{specs,plans}/2026-06-16-installer-tui-*.md`
7. 修改 `README.md`（快速安装段）
8. 修改 `DEPLOY.md`（删除 installer 相关行）
9. 修改 `CLAUDE.md`（重构优先级 #5）
10. 修改 `docs/architecture/modules.md`（顶部段 + 重构优先级段）
11. 跑验证清单 1-7
12. 走代理 push

## 不实施

- 不重写 git 历史（已在前次 squash）
- 不再次 squash
- 不改 `jprompt` 子模块
- 不动 `frontend/`、`Dockerfile`、`docker-compose.yml` 主体
- 不改业务代码、import 路径、API
