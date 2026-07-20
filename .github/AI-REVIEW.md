# AI Code Review（GitHub Actions + 任意 Anthropic 兼容 LLM）

PR 提交时由 GitHub Actions 自动调一个 Anthropic 协议的 LLM 做评审（默认 MiniMax-M3，可换任何 Anthropic 兼容 provider），结果作为单条 PR 评论回写。

## 配置（一次性）

仓库管理员在 **Settings → Secrets and variables → Actions** 添加以下条目：

| 类型 | 名称 | 默认值 | 说明 |
|---|---|---|---|
| Secret | `LLM_API_KEY` | （必填） | 你接入的 LLM 服务的 API key |
| Variable | `LLM_BASE_URL` | `https://api.minimaxi.com/anthropic` | 任何 Anthropic 兼容端点（如 https://api.anthropic.com / 自部署网关） |
| Variable | `LLM_MODEL_ID` | `MiniMax-M3` | 任意该 provider 支持的模型 ID |

> 三者命名为 `LLM_*` 前缀，刻意不绑定 provider 品牌。未来切换 provider（MiniMax → Anthropic → 自部署 / 其它兼容服务）只需改 value，不需要动 workflow。

## 行为

- 触发时机：`pull_request: opened / ready_for_review / synchronize / reopened`
- 评审范围：`git diff origin/${{ base_ref }}...HEAD`，超过 12 万字符会截断。
- 输出形式：**单条 PR 评论**，评论头含 `## 🤖 AI Code Review (<MODEL_ID>)`，下次 `synchronize` 时会原地更新该评论（避免堆评论）。
- 未配置 `LLM_API_KEY` 时：跳过评审 + 留一条提醒评论（用 `marocchino/sticky-pull-request-comment`）。
- 超时：`timeout-minutes: 10`。

## 评审维度

详见 `.github/ai-review-prompt.md`，要点：

1. 架构与分层（模块依赖方向、Port/Infra、跨聚合走事件）
2. OOP 七原则 + 单业务单类
3. 命名（camelCase、布尔不带 is/has）
4. Lombok / 异常 / 事务与事件 / Java 禁用 record
5. API / 测试 / 安全 / 前端规范
6. 通用质量（去重、组合而非继承）

## 本地自测

```bash
export LLM_API_KEY=...
export LLM_MODEL_ID=MiniMax-M3
export PR_NUMBER=1
export PR_TITLE="test"
export PR_BODY=""
export PR_BASE_REF=master
export GITHUB_REPOSITORY=welsione/Ascoder
export GITHUB_TOKEN=<your PAT with public_repo scope>

# 切到 PR 分支
git checkout worktree-runtime-settings
npm install --no-save @anthropic-ai/sdk
node .github/scripts/ai-review.mjs
```

脚本会调模型，并通过 `GITHUB_TOKEN` 把评论写到 PR。调试用 PAT 需要 `public_repo` 权限。

## 切到不同 provider

只需覆盖 variables / secrets：

| Provider | LLM_API_KEY 取自 | LLM_BASE_URL | LLM_MODEL_ID 例子 |
|---|---|---|---|
| MiniMax | https://api.minimaxi.com | `https://api.minimaxi.com/anthropic` | `MiniMax-M3` |
| Anthropic 官方 | https://console.anthropic.com | `https://api.anthropic.com` | `claude-3-7-sonnet-...` |
| 任意 Anthropic 兼容中转 / 自部署 | 服务方提供 | 服务方提供 | 服务方支持列表 |

不需要改任何代码。
