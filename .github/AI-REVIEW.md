# AI Code Review（GitHub Actions + MiniMax）

PR 提交时由 GitHub Actions 自动调 MiniMax（Anthropic 兼容端点）做一轮代码评审，结果作为单条 PR 评论回写。

## 配置（一次性）

仓库管理员在 **Settings → Secrets and variables → Actions** 添加以下条目：

| 类型 | 名称 | 默认值 | 说明 |
|---|---|---|---|
| Secret | `ANTHROPIC_API_KEY` | （必填） | 从 https://api.minimaxi.com 申请的 MiniMax key |
| Variable | `MINIMAX_BASE_URL` | `https://api.minimaxi.com/anthropic` | Anthropic 兼容端点 |
| Variable | `MINIMAX_MODEL_ID` | `MiniMax-M3` | 与本地 `application-local.yml` 默认值一致 |

## 行为

- 触发时机：`pull_request: opened / ready_for_review / synchronize / reopened`
- 评审范围：`git diff origin/${{ base_ref }}...HEAD`，超过 12 万字符会截断。
- 输出形式：**单条 PR 评论**，评论头含 `## 🤖 AI Code Review (MiniMax-M3)`，下次 `synchronize` 时会原地更新该评论（避免堆评论）。
- 未配置 `ANTHROPIC_API_KEY` 时：跳过评审 + 留一条提醒评论（用 `marocchino/sticky-pull-request-comment`）。
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
export ANTHROPIC_API_KEY=...
export MINIMAX_MODEL_ID=MiniMax-M3
export PR_NUMBER=1
export PR_TITLE="test"
export PR_BODY=""
export PR_BASE_REF=master
export GITHUB_REPOSITORY=welsione/Ascoder
export GITHUB_TOKEN=<your PAT with repo:public_access>

# 切到某 PR 分支
git checkout worktree-runtime-settings
node .github/scripts/ai-review.mjs
```

调试用 PAT 需要 `public_repo` 权限。