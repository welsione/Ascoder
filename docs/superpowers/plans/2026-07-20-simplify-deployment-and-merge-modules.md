# Ascoder 简化部署与合并 Maven 模块实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 合并 `ascoder-common` 与 `ascoder-codegraph` 两个 Maven 模块到 `backend`，删除非 Docker 部署资产（`installer/`、`scripts/{package,deploy}.{sh,ps1}`、installer TUI 历史设计文档），改写 README/DEPLOY/CLAUDE.md/modules.md 为纯 Docker 部署叙事。

**Architecture:** 采用单体 Spring Boot fat jar + 物理 Maven 模块最小化（`jprompt` 独立 + `backend` 一体）。源文件 `git mv` 进 `backend` 保留原包路径，pom 依赖合并，文档与脚本同步精简。最终 master 在前次 squash 单 commit 之上叠 1 个新 commit。

**Tech Stack:** Maven 3.9 / Java 17 / Spring Boot 3.3.7 / Docker / Docker Compose / Git

---

## Task 1: 移动 ascoder-common 源码到 backend

**Files:**
- Move: `ascoder-common/src/main/java/cn/welsione/ascoder/common/**` (17 java) → `backend/src/main/java/cn/welsione/ascoder/common/**`
- Move: `ascoder-common/src/test/java/cn/welsione/ascoder/common/security/ApiKeyEncryptorTests.java` → `backend/src/test/java/cn/welsione/ascoder/common/security/ApiKeyEncryptorTests.java`

- [ ] **Step 1: 用 git mv 移动所有 main java**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
mkdir -p backend/src/main/java/cn/welsione/ascoder/common
git mv ascoder-common/src/main/java/cn/welsione/ascoder/common/*.java \
       backend/src/main/java/cn/welsione/ascoder/common/
```

- [ ] **Step 2: 移动子包（exception、security）**

```bash
git mv ascoder-common/src/main/java/cn/welsione/ascoder/common/exception \
       backend/src/main/java/cn/welsione/ascoder/common/exception
git mv ascoder-common/src/main/java/cn/welsione/ascoder/common/security \
       backend/src/main/java/cn/welsione/ascoder/common/security
```

- [ ] **Step 3: 移动测试**

```bash
mkdir -p backend/src/test/java/cn/welsione/ascoder/common/security
git mv ascoder-common/src/test/java/cn/welsione/ascoder/common/security/ApiKeyEncryptorTests.java \
       backend/src/test/java/cn/welsione/ascoder/common/security/ApiKeyEncryptorTests.java
```

- [ ] **Step 4: 验证文件已移动且原位置已清空**

```bash
ls backend/src/main/java/cn/welsione/ascoder/common/ | head
# 期望看到: AbortSignal.java AsyncCommandRunner.java CommandResult.java ...
ls ascoder-common/src/main/java/cn/welsione/ascoder/ 2>&1
# 期望: No such file or directory
```

- [ ] **Step 5: 暂存但不提交**

```bash
git status --short | head -20
# 期望: R 类条目（重命名），暂不 commit
```

---

## Task 2: 移动 ascoder-codegraph 源码到 backend

**Files:**
- Move: `ascoder-codegraph/src/main/java/cn/welsione/ascoder/codegraph/**` (6 java) → `backend/src/main/java/cn/welsione/ascoder/codegraph/**`
- Move: `ascoder-codegraph/src/test/java/cn/welsione/ascoder/codegraph/infrastructure/cli/CodeGraphCommandRunnerTests.java` → `backend/src/test/java/cn/welsione/ascoder/codegraph/infrastructure/cli/CodeGraphCommandRunnerTests.java`

- [ ] **Step 1: 移动 main java 与子包**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
mkdir -p backend/src/main/java/cn/welsione/ascoder/codegraph
git mv ascoder-codegraph/src/main/java/cn/welsione/ascoder/codegraph/CodeGraphConfig.java \
       backend/src/main/java/cn/welsione/ascoder/codegraph/CodeGraphConfig.java
git mv ascoder-codegraph/src/main/java/cn/welsione/ascoder/codegraph/infrastructure \
       backend/src/main/java/cn/welsione/ascoder/codegraph/infrastructure
git mv ascoder-codegraph/src/main/java/cn/welsione/ascoder/codegraph/port \
       backend/src/main/java/cn/welsione/ascoder/codegraph/port
```

- [ ] **Step 2: 移动测试**

```bash
mkdir -p backend/src/test/java/cn/welsione/ascoder/codegraph/infrastructure/cli
git mv ascoder-codegraph/src/test/java/cn/welsione/ascoder/codegraph/infrastructure/cli/CodeGraphCommandRunnerTests.java \
       backend/src/test/java/cn/welsione/ascoder/codegraph/infrastructure/cli/CodeGraphCommandRunnerTests.java
```

- [ ] **Step 3: 验证移动结果**

```bash
find backend/src/main/java/cn/welsione/ascoder/codegraph -name '*.java' | sort
# 期望: CodeGraphConfig.java + infrastructure/cli/{CliCodeGraphClient,CodeGraphCommandRunner,IndexProgressTracker}.java + port/{CodeGraphClient,CodeGraphToolResult}.java
ls ascoder-codegraph/src/ 2>&1
# 期望: No such file or directory
```

- [ ] **Step 4: 暂存但不提交**

```bash
git status --short | grep codegraph | head
# 期望: R 类条目
```

---

## Task 3: 删除两个模块的 pom.xml 和目录

**Files:**
- Delete: `ascoder-common/pom.xml`、`ascoder-codegraph/pom.xml`
- Delete: `ascoder-common/` 目录（含 `.gitignore` 残件）
- Delete: `ascoder-codegraph/` 目录（含 `.gitignore` 残件）

- [ ] **Step 1: 检查两个目录的剩余内容（应只剩空目录或 .DS_Store）**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
find ascoder-common -type f 2>/dev/null
find ascoder-codegraph -type f 2>/dev/null
```

- [ ] **Step 2: 用 git rm 删除空目录（带 -r 防有隐藏文件）**

```bash
git rm -r ascoder-common/pom.xml ascoder-codegraph/pom.xml 2>&1
git rm -r ascoder-common ascoder-codegraph 2>&1 | tail -5
```

- [ ] **Step 3: 验证删除**

```bash
ls ascoder-common 2>&1; ls ascoder-codegraph 2>&1
# 期望: No such file or directory (两次)
git status --short | grep -E 'ascoder-(common|codegraph)/' | head
# 期望: 无输出
```

---

## Task 4: 修改根 pom.xml 移除两个模块

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 读取当前 modules 节点确认**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
cat pom.xml
```

- [ ] **Step 2: 用 Edit 替换 modules 节点**

```bash
# 手动 Edit 工具
# old_string:
#     <modules>
#         <module>ascoder-common</module>
#         <module>ascoder-codegraph</module>
#         <module>jprompt</module>
#         <module>backend</module>
#     </modules>
# new_string:
#     <modules>
#         <module>jprompt</module>
#         <module>backend</module>
#     </modules>
```

用 Edit 工具：
- old: `        <module>ascoder-common</module>\n        <module>ascoder-codegraph</module>\n        <module>jprompt</module>\n        <module>backend</module>`
- new: `        <module>jprompt</module>\n        <module>backend</module>`

- [ ] **Step 3: 验证修改**

```bash
grep -A4 '<modules>' pom.xml
# 期望:
#     <modules>
#         <module>jprompt</module>
#         <module>backend</module>
#     </modules>
```

---

## Task 5: 修改 backend/pom.xml 合并依赖

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: 读取当前 dependencies 段定位两个 internal dependency**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
grep -nE 'ascoder-common|ascoder-codegraph' backend/pom.xml
```

- [ ] **Step 2: 删除两个 internal dependency（共 12 行）**

用 Edit 工具一次删两段：
- old:
```
        <dependency>
            <groupId>cn.welsione</groupId>
            <artifactId>ascoder-common</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>cn.welsione</groupId>
            <artifactId>ascoder-codegraph</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
```
- new: （空）

- [ ] **Step 3: 在 jprompt 依赖前插入三个新依赖（jackson、slf4j、assertj）**

找到 `<groupId>cn.welsione</groupId>\n        <artifactId>jprompt</artifactId>`，在它前面插入：

```xml
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.17.0</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.13</version>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.26.3</version>
            <scope>test</scope>
        </dependency>
```

用 Edit 工具：
- old: `        <dependency>\n            <groupId>cn.welsione</groupId>\n            <artifactId>jprompt</artifactId>`
- new: `<上面三个 dependency XML>\n        <dependency>\n            <groupId>cn.welsione</groupId>\n            <artifactId>jprompt</artifactId>`

- [ ] **Step 4: 验证 pom 改动**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
grep -nE 'ascoder-common|ascoder-codegraph|jackson-databind|<artifactId>slf4j-api|<artifactId>assertj-core' backend/pom.xml
# 期望: 命中 jackson-databind/slf4j-api/assertj-core 三行,不命中 ascoder-common/ascoder-codegraph
```

---

## Task 6: 首次构建验证（确认 pom 合并正确）

**Files:** 无（验证步骤）

- [ ] **Step 1: 跑 mvn validate 检查 pom 解析**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
mvn -pl backend -am validate -q
```

预期：BUILD SUCCESS（pom 无错）

- [ ] **Step 2: 跑 mvn compile 检查源码能编译**

```bash
mvn -pl backend -am compile -DskipTests -q
```

预期：BUILD SUCCESS（源码能编译）

- [ ] **Step 3: 跑 mvn test-compile 检查测试能编译**

```bash
mvn -pl backend -am test-compile -q
```

预期：BUILD SUCCESS（测试类能编译）

---

## Task 7: 跑全部测试（确认 0 个测试回归）

**Files:** 无（验证步骤）

- [ ] **Step 1: 跑 backend 全部测试**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
mvn -pl backend -am test 2>&1 | tail -30
```

预期：BUILD SUCCESS，所有测试通过（包含迁移过来的 `ApiKeyEncryptorTests`、`CodeGraphCommandRunnerTests`）

---

## Task 8: 删除 installer/ 目录

**Files:**
- Delete: `installer/` 全部 11 文件

- [ ] **Step 1: 检查 installer 目录内容**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
find installer -type f
```

- [ ] **Step 2: 用 git rm -r 删除**

```bash
git rm -r installer
```

- [ ] **Step 3: 验证删除**

```bash
ls installer 2>&1
# 期望: No such file or directory
git ls-files | grep '^installer/' | head
# 期望: 无输出
```

---

## Task 9: 删除 scripts/{package.sh, deploy.sh, deploy.ps1}

**Files:**
- Delete: `scripts/package.sh`
- Delete: `scripts/deploy.sh`
- Delete: `scripts/deploy.ps1`

- [ ] **Step 1: 删除三个文件**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
git rm scripts/package.sh scripts/deploy.sh scripts/deploy.ps1
```

- [ ] **Step 2: 验证 scripts/ 仅剩 verify-docker.sh**

```bash
ls scripts/
# 期望: verify-docker.sh
git ls-files scripts/ | head
# 期望: scripts/verify-docker.sh
```

---

## Task 10: 删除 installer TUI 历史设计文档

**Files:**
- Delete: `docs/superpowers/specs/2026-06-16-installer-tui-design.md`
- Delete: `docs/superpowers/plans/2026-06-16-installer-tui.md`

- [ ] **Step 1: 删除两个文件**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
git rm docs/superpowers/specs/2026-06-16-installer-tui-design.md \
       docs/superpowers/plans/2026-06-16-installer-tui.md
```

- [ ] **Step 2: 验证其他 docs/superpowers/ 文件无 installer 引用**

```bash
grep -lE 'install\.sh|install\.ps1|installer' docs/superpowers/specs/*.md docs/superpowers/plans/*.md 2>/dev/null
# 期望: 无输出（剩余文件与 installer 无关）
```

---

## Task 11: 修改 README.md 替换为 docker compose 引导

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 读取当前快速安装段**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
grep -nE 'install\.|installer' README.md | head
```

- [ ] **Step 2: 用 Edit 替换第 27 行附近 `./installer/install.sh`**

- old: `./installer/install.sh`
- new: `docker compose up -d`

- [ ] **Step 3: 用 Edit 替换第 33 行附近 `.\installer\install.ps1`**

- old: `.\installer\install.ps1`
- new: `docker compose up -d`

- [ ] **Step 4: 验证**

```bash
grep -nE 'install\.|installer' README.md
# 期望: 无输出
```

---

## Task 12: 修改 DEPLOY.md 移除 installer 相关行

**Files:**
- Modify: `DEPLOY.md`

- [ ] **Step 1: 定位需要删除的行**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
grep -nE 'install\.sh|install\.ps1|uninstall\.|java -jar|launchd|systemd|升级指引|ExecutionPolicy' DEPLOY.md
```

- [ ] **Step 2: 删除第 192 行附近（PowerShell ExecutionPolicy）**

用 Edit 删单行：
- old: `| PowerShell ExecutionPolicy | \`install.ps1\` 运行报"running scripts is disabled" | \`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned\` 或 \`powershell -ExecutionPolicy Bypass -File .\\installer\\install.ps1\` |\n`
- new: （空）

- [ ] **Step 3: 删除第 196 行附近（旧版 install.sh launchd/systemd）**

用 Edit 删单行：
- old: `| 旧版 install.sh 注册过 launchd / systemd | 升级后 \`docker compose up\` 报端口占用 | \`launchctl unload\` / \`systemctl stop ascoder\` + 删除 plist/unit |\n`
- new: （空）

- [ ] **Step 4: 删除"升级指引"整段（第 198–206 行附近，"### 升级指引" + 后续 7 行）**

读取上下文确认段落起止：

```bash
sed -n '195,210p' DEPLOY.md
```

用 Edit 一次删整段：
- old: `### 升级指引\n\n如果你之前使用旧版 \`install.sh\` + \`java -jar\` 部署方式，升级到 Docker Compose 模式需要：\n\n1. **停止旧服务**：\n...\n3. **重新安装**：\`./installer/install.sh\`（新版本会走 Docker Compose）\n`
- new: （空）

- [ ] **Step 5: 验证**

```bash
grep -nE 'install\.|installer|java -jar|launchd|systemd|升级指引' DEPLOY.md
# 期望: 无输出
```

---

## Task 13: 修改 CLAUDE.md 重构优先级 #5

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 定位原 #5 文字**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
grep -n '在边界稳定后拆分 Maven module\|5. ' CLAUDE.md | head
```

- [ ] **Step 2: 替换重构优先级 #5**

- old: `5. 在边界稳定后拆分 Maven module。`
- new: `5. 当前采用单体 Spring Boot + 物理 Maven 模块最小化（\`jprompt\` 独立 + \`backend\` 一体），如需进一步拆分再评估。`

- [ ] **Step 3: 在"模块边界"段后新增一句指向物理 pom**

找到"模块边界"段末尾（`## 面向对象与代码复用` 之前）：

- old: `## 面向对象与代码复用`
- new: `> 物理 Maven 模块见根 \`pom.xml\` 的 \`<modules>\` 节点（合并后仅 \`jprompt\` + \`backend\`）。\n\n## 面向对象与代码复用`

- [ ] **Step 4: 验证**

```bash
grep -nE '在边界稳定后拆分|单体 Spring Boot|jprompt.*backend' CLAUDE.md
# 期望: 命中后两条新文字,不命中第一条
```

---

## Task 14: 修改 docs/architecture/modules.md

**Files:**
- Modify: `docs/architecture/modules.md`

- [ ] **Step 1: 读取顶部段确认**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
head -5 docs/architecture/modules.md
```

- [ ] **Step 2: 替换顶部段**

- old: `本文档描述 Ascoder v1 的第一版能力模块边界。当前阶段先保持单体应用形态，通过包边界、端口接口和事件隔离约束依赖方向；边界稳定后再拆 Maven module。`
- new: `本文档描述 Ascoder 的逻辑模块边界（与 Maven 物理模块解耦，作为包内组织指引）。当前阶段保持单体应用形态，通过包边界、端口接口和事件隔离约束依赖方向。`

- [ ] **Step 3: 删除"重构优先级"段中的第 5 条**

读取"重构优先级"段确认行号：
```bash
grep -n '重构优先级\|5\. ' docs/architecture/modules.md
```

- old: `5. 在边界稳定后拆分 Maven module。\n`
- new: （空，仅删这一行）

- [ ] **Step 4: 验证**

```bash
grep -nE '在边界稳定后拆分|物理模块解耦' docs/architecture/modules.md
# 期望: 命中"物理模块解耦"新文字,不命中"在边界稳定后拆分"
```

---

## Task 15: 跑全套验证（验证清单 1-7）

**Files:** 无（验证步骤）

- [ ] **Step 1: 验证清单 1 — mvn clean package**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
mvn -pl backend -am clean package -DskipTests 2>&1 | tail -10
```

预期：BUILD SUCCESS

- [ ] **Step 2: 验证清单 2 — mvn test**

```bash
mvn -pl backend -am test 2>&1 | tail -10
```

预期：BUILD SUCCESS

- [ ] **Step 3: 验证清单 3 — 无 installer/ascoder-common/ascoder-codegraph 跟踪**

```bash
git ls-files | grep -E '^installer/|^ascoder-common/|^ascoder-codegraph/'
# 期望: 无输出
```

- [ ] **Step 4: 验证清单 4 — 无 scripts/{package,deploy} 跟踪**

```bash
git ls-files | grep -E '^scripts/(package|deploy)'
# 期望: 无输出
```

- [ ] **Step 5: 验证清单 5 — 无 install.sh/ps1 残留引用**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
grep -rE 'install\.sh|install\.ps1|uninstall\.(sh|ps1)|scripts/package\.sh|scripts/deploy\.' \
  --include='*.md' --include='*.yml' --include='*.sh' --include='*.json' . 2>/dev/null \
  | grep -vE '/target/|/\.git/|Ascoder\.git\.bak|/data/|/node_modules/|docs/superpowers/specs/2026-07-20'
# 期望: 无输出
```

- [ ] **Step 6: 验证清单 6 — docker compose config（如有 Docker 环境）**

```bash
docker compose config --quiet 2>&1
# 期望: 无错误输出,exit code 0
# 若 Docker 不可用,此步跳过
```

- [ ] **Step 7: 验证清单 7 — 无 pom.xml 引用 ascoder-common/ascoder-codegraph**

```bash
grep -rE 'ascoder-common|ascoder-codegraph' --include='pom.xml' .
# 期望: 无输出
```

---

## Task 16: 提交并推送

**Files:** 无（git 操作）

- [ ] **Step 1: 查看 git status 确认变更范围**

```bash
cd /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder
git status --short
# 期望: R 类(重命名) + D 类(删除) + M 类(修改 pom/文档) + ?? 类(无)
```

- [ ] **Step 2: 暂存所有变更**

```bash
git add -A
git status --short | head
# 期望: 第一列无 ?? 标记(都暂存了)
```

- [ ] **Step 3: 提交**

```bash
git commit -m "$(cat <<'EOF'
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
EOF
)"
```

- [ ] **Step 4: 走代理 push**

```bash
git -c http.proxy=http://127.0.0.1:9981 \
    -c https.proxy=http://127.0.0.1:9981 \
    push origin master 2>&1 | tail -5
```

预期：`3a55918..<新hash> master -> master`

---

## Self-Review

**1. Spec 覆盖检查**

| Spec 节 | 任务 |
|---|---|
| ① 物理结构（common/codegraph → backend） | Task 1, 2, 3 |
| ② 依赖与构建（根 pom、backend pom、scripts 删、verify-docker 留、Dockerfile 不变） | Task 4, 5, 9 |
| ③ 删除非 Docker 部署（installer/、scripts/{package,deploy}、installer TUI 历史文档） | Task 8, 9, 10 |
| ④ 文档清理（README、DEPLOY、CLAUDE.md、modules.md） | Task 11, 12, 13, 14 |
| ⑤ 验证清单 1-8 | Task 6, 7, 15, 16 |

全部覆盖 ✓

**2. 占位扫描**

- "用 Edit 工具" / "用 Edit 工具一次删两段" — 这些是工具引用，不是 TBD。工程师按字面用 Edit 即可 ✓
- 验证步骤的"期望: BUILD SUCCESS" / "期望: 无输出" — 明确预期，不是 TBD ✓
- "用 sed 读取上下文" — 这是定位步骤，不是实现模糊 ✓

**3. 一致性**

- "ascoder-common" / "ascoder-codegraph" 命名在所有 task 一致 ✓
- "jackson-databind 2.17.0" / "slf4j-api 2.0.13" / "assertj-core 3.26.3" 版本号在 Task 5 定义、Task 6/7 验证、Task 15 验证一致 ✓
- commit message 复用 spec 中的版本 ✓

修复一处：Task 6/7 跑 mvn 时使用 `mvn -pl backend -am`，与 spec 中"scripts/package.sh 用 mvn -pl backend -am"一致 ✓

**4. 反例检查**

- 没有"类似 Task 5" / "同前" 这种引用前文代码而不复述的步骤 ✓
- 每步有完整命令或完整 diff ✓
- 路径绝对或相对都明确 ✓
