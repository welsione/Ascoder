# 重构方案：代码规范合规性修复

基于 CLAUDE.md / AGENTS.md 规范审查，按优先级列出所有需修复项。

---

## 一、SelfLearningService 拆分（🔴 高优先级）

### 问题

`SelfLearningService`（1687 行）同时承担 7 个互不相关的业务职责，注入 17 个依赖，暴露 40+ 个 public 方法。违反「单业务单类」和「跨聚合禁止直接操作 Repository」规范。

### 拆分方案

将 `SelfLearningService` 拆为 6 个独立 Service，每个只负责一个聚合根的 CRUD + 业务逻辑：

```
selflearning/
├── SelfLearningService.java          → 仅保留 summary + settings（2 个方法）
├── InsightService.java               → 从 SelfLearningService 拆出（8 个方法）
├── KnowledgeService.java             → 从 SelfLearningService 拆出（7 个方法）
├── ExperienceService.java            → 从 SelfLearningService 拆出（8 个方法）
├── TermService.java                  → 从 SelfLearningService 拆出（4 个方法）
├── CorrectionService.java            → 从 SelfLearningService 拆出（6 个方法）
├── AgentRunService.java              → 从 SelfLearningService 拆出（6 个方法）
└── SelfLearningContextBuilder.java   → 从 SelfLearningService 拆出 buildContext 及相关私有方法
```

#### 各 Service 职责与依赖

| Service | 方法 | 需要的依赖 |
|---|---|---|
| `SelfLearningService` | `summary`, `getSettings`, `updateSettings` | `settingsRepository`, 各 repo（仅 summary 计数用）, `projectSpaceService` |
| `InsightService` | `list/create/update/approve/reject/verify/refine/cleanup` | `insightRepository`, `insightStateMachine`, `insightReviewAgent`, `insightFieldTruncator`, `knowledgeRepository`, `rawEventRepository`, `projectSpaceService` |
| `KnowledgeService` | `list/create/update/delete/archive/markStale` | `knowledgeRepository`, `projectSpaceService` |
| `ExperienceService` | `list/get/create/update/delete/verify/reject/archive` | `experienceRepository`, `projectSpaceService` |
| `TermService` | `list/create/update/delete` | `termRepository`, `projectSpaceService` |
| `CorrectionService` | `list/create/update/delete/verify/reject` | `correctionRepository`, `projectSpaceService` |
| `AgentRunService` | `run/create/markRunning/complete/fail/list` | `agentRunRepository`, `settingsRepository`, `rawEventRepository`, `insightRepository`, `knowledgeRepository`, `insightAgent`, `insightFieldTruncator`, `transactionTemplate`, `objectMapper`, `projectSpaceService` |
| `SelfLearningContextBuilder` | `buildContext` | `knowledgeRepository`, `settingsRepository`, `experienceRepository`, `termRepository`, `correctionRepository` |

#### 跨模块依赖处理

`SelfLearningService` 当前直接依赖 `question` 和 `repository` 模块的 Repository，需要消除：

**1. `createCandidateFromAnswer`（selflearning → question）**

当前：`SelfLearningService` 直接调用 `QuestionJpaRepository.findById()` 和 `QueryPlanJpaRepository.findByQuestionIdIn()`

方案：改为通过领域事件解耦。`QuestionService` 在回答完成后发布 `QuestionAnsweredEvent`，`SelfLearningService` 监听该事件。

```
QuestionService.complete() 
  → 发布 QuestionAnsweredEvent(questionId, response, fullAnswer)
    → AgentRunService（或新建 RawEventCaptureService）监听并调用 createCandidateFromAnswer
```

**2. `importHistoryRawEvents`（selflearning → question）**

当前：直接调用 `QuestionJpaRepository` 查询历史问题

方案：在 `question` 模块新增 `QuestionQueryPort` 接口，`SelfLearningService` 通过 Port 查询。

```java
// question 模块定义端口
public interface QuestionQueryPort {
    List<QuestionSummary> findByProjectSpaceId(Long projectSpaceId);
}

// selflearning 通过 Port 调用，不直接依赖 QuestionJpaRepository
```

**3. `codeRepositoryRepository`（selflearning → repository）**

当前：直接调用 `CodeRepositoryJpaRepository.findById()`

方案：在 `repository` 模块新增 `RepositoryQueryPort`，或在需要 `CodeRepository` 的地方改为接收 ID 并由调用方传入已解析的实体。

**4. `projectSpaceService`（selflearning → repository）**

当前：几乎所有方法都调用 `projectSpaceService.getEntity()` 校验项目空间存在性

方案：保留此依赖。`projectSpaceService.getEntity()` 是读操作，且 projectSpace 是 selflearning 的父聚合，通过 Service 调用（而非 Repository）是合理的。

#### Controller 适配

`SelfLearningController` 当前全部委托给 `SelfLearningService`，拆分后改为注入多个 Service：

```java
@RestController
@RequiredArgsConstructor
public class SelfLearningController {
    private final SelfLearningService selfLearningService;
    private final InsightService insightService;
    private final KnowledgeService knowledgeService;
    private final ExperienceService experienceService;
    private final TermService termService;
    private final CorrectionService correctionService;
    private final AgentRunService agentRunService;
}
```

#### QuestionStreamService 适配

`QuestionStreamService` 当前直接注入 `SelfLearningService`，拆分后改为注入 `SelfLearningContextBuilder`（仅需要 `buildContext`）和 `AgentRunService`（仅需要 `createCandidateFromAnswer`，改为事件驱动后此依赖消失）。

---

## 二、AgentToolingService 拆分（🔴 高优先级）

### 问题

`AgentToolingService`（330 行）注入 13 个依赖，同时为 Orchestrator 和 Researcher 两种角色构建工具集，且两组依赖完全不重叠。违反「单业务单类」。

### 拆分方案

```
agent/infrastructure/agentscope/
├── AgentToolingService.java       → 保留为门面，委托给下面两个 Builder
├── OrchestratorToolingBuilder.java → 从 buildForOrchestrator 拆出
├── ResearcherToolingBuilder.java   → 从 buildForResearcher 拆出
└── McpClientFactory.java          → 从 createClient + parseStringList/Map 拆出
```

#### 各类职责与依赖

| 类 | 职责 | 依赖 |
|---|---|---|
| `AgentToolingService` | 门面，`buildForOrchestrator` 委托给 `OrchestratorToolingBuilder`，`buildForResearcher` 委托给 `ResearcherToolingBuilder` | `OrchestratorToolingBuilder`, `ResearcherToolingBuilder` |
| `OrchestratorToolingBuilder` | 构建 Orchestrator 工具集：注册 Skill + MCP Server | `skillRepository`, `mcpServerRepository`, `mcpClientFactory`, `objectMapper` |
| `ResearcherToolingBuilder` | 构建 Researcher 工具集：注册 7 个代码分析工具组 + Log 工具 | `codeGraphClient`, `gitRepositoryService`, `safeCommandRunner`, `toolService`, `filePathSanitizer`, `logUploadService`, `logMaskingService`, `objectMapper` |
| `McpClientFactory` | MCP 客户端构建 + JSON 解析 | `objectMapper` |

#### 跨模块依赖处理

`ResearcherToolingBuilder` 直接依赖 `selflearning` 模块的 3 个 Repository：

```java
import selflearning.LearningKnowledgeItemJpaRepository
import selflearning.LearningInsightJpaRepository
import selflearning.LearningRawEventJpaRepository
```

方案：在 `selflearning` 模块新增 `SelfLearningQueryPort`：

```java
// selflearning 模块定义端口
public interface SelfLearningQueryPort {
    List<LearningKnowledgeItem> findKnowledgeByProjectSpaceId(Long projectSpaceId);
    List<LearningInsight> findPendingInsights(Long projectSpaceId);
    List<LearningRawEvent> findRecentRawEvents(Long projectSpaceId, int limit);
}

// ResearcherToolingBuilder 通过 Port 调用，SelfLearningAgentTools 改为接收查询结果而非 Repository
```

---

## 三、Question.java 跨模块实体引用（🔴 高优先级）

### 问题

`question.domain.Question` 直接 import `repository` 模块的实体类：

```java
import repository.workspace.BranchWorkspace;
import repository.projectspace.ProjectSpace;
import repository.CodeRepository;
```

### 方案

改为通过 ID 引用 + Snapshot 模式：

1. `Question` 实体只保留 `projectSpaceId`（Long），移除 `@ManyToOne ProjectSpace`
2. 需要项目空间信息时，由 Service 层调用 `ProjectSpaceService.getEntity()` 获取
3. `BranchWorkspace` 同理，改为 `branchWorkspaceId`

这是较大的改动，需要同步修改：
- `Question` 实体字段
- `AgentRequestBuilder` 构建 `AgentRequest` 时补充项目空间信息
- 相关 Flyway 迁移

---

## 四、前端 Store 重复代码（🟡 中优先级）

### 问题

8 个 Store 全部重复 `loading` / `error` / `fetch` / `create` 样板代码，错误处理完全相同。

### 方案

新增 `src/composables/useCrudStore.ts`：

```typescript
interface CrudStoreOptions<T, F extends Record<string, unknown>> {
  apiFetch: () => Promise<T[]>
  apiCreate: (form: F) => Promise<T>
  apiDelete?: (id: number) => Promise<void>
  initialForm: () => F
  validate?: (form: F) => string | null
}

function useCrudStore<T extends { id: number }, F extends Record<string, unknown>>(
  options: CrudStoreOptions<T, F>
) {
  const items = ref<T[]>([]) as Ref<T[]>
  const loading = ref(false)
  const createLoading = ref(false)
  const error = ref('')

  async function fetch() {
    loading.value = true; error.value = ''
    try { items.value = await options.apiFetch() }
    catch (e) { error.value = extractErrorMessage(e) }
    finally { loading.value = false }
  }

  async function create() {
    const validationError = options.validate?.(form.value)
    if (validationError) { error.value = validationError; return null }
    createLoading.value = true; error.value = ''
    try {
      const created = await options.apiCreate(form.value)
      resetForm()
      await fetch()
      return created
    } catch (e) { error.value = extractErrorMessage(e); return null }
    finally { createLoading.value = false }
  }

  // ... resetForm, per-item busy tracking 等
}

function extractErrorMessage(err: unknown, fallback = '操作失败'): string {
  return err instanceof Error ? err.message : fallback
}
```

各 Store 改为基于 `useCrudStore` 扩展，仅补充自身特有的状态和方法。

---

## 五、Javadoc 补全（🟡 中优先级）

### 缺失清单

| 模块 | 缺失数 | 关键类 |
|---|---|---|
| repository | 21 | `ProjectSpace`, `ProjectSpaceMember`, `BranchWorkspace`, 各 JpaRepository |
| loganalysis | 6 | 待逐一排查 |
| analysis | 2 | `CodeGraphWorkspaceContext` |
| agent | 2 | 待逐一排查 |
| question | 2 | `SseConnectionManager`, `AgentEventDispatcher` |
| common | 1 | `AbortSignal` |

### 标准

每个类必须添加类级 Javadoc，格式：

```java
/**
 * 洞察状态机，管理 Insight 的状态转换规则。
 */
```

---

## 六、Flyway 索引命名修正（🟢 低优先级）

### 问题

5 个迁移文件的索引名使用 snake_case 前缀 `idx_`：

```sql
idx_learningExperiences_space_status
idx_agentEvent_question
idx_learningRawEvents_space_type
idx_learningInsights_space_status
idx_learningKnowledgeItems_space_status
idx_learningAgentRuns_space_status
idx_learningAgentRuns_space_created
idx_learningRawEvents_space_created
idx_learningRawEvents_question
idx_learningInsights_space_type
idx_learningKnowledgeItems_space_type
idx_logUploads_projectSpace_createdAt
idx_logFiles_upload
idx_logAnalysisTasks_question
```

### 方案

新增一个迁移文件 `V28__normalize_index_names.sql`，重命名所有索引：

```sql
ALTER TABLE learningExperiences RENAME INDEX idx_learningExperiences_space_status TO idxLearningExperiencesSpaceStatus;
ALTER TABLE learningExperiences RENAME INDEX idx_learningExperiences_space_type TO idxLearningExperiencesSpaceType;
-- ... 依次修正
```

---

## 实施顺序

```
Phase 1（本周）：SelfLearningService 拆分 + AgentToolingService 拆分
Phase 2（下周）：跨模块依赖消除（Port 接口 + 领域事件）
Phase 3（下周）：Question.java 实体引用改造
Phase 4（持续）：前端 Store 泛型抽取 + Javadoc 补全
Phase 5（低优）：Flyway 索引命名修正
```

Phase 1 和 Phase 2 可以并行，但 Phase 2 依赖 Phase 1 的拆分结果。Phase 3 独立性较强可以和 Phase 4 并行。
