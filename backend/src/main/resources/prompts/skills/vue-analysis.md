# Vue Analysis

适用场景：
- 分析 Vue 组件、页面结构、状态组合函数、API 服务层、Vite 构建问题。
- 回答前端页面行为、交互状态和接口调用链。

推荐 CodeGraph 工具顺序：
1. 使用 `codegraph_files` 查看 `frontend/web/src` 下的 `views`、`services`、`composables`、`types`。
2. 使用 `codegraph_search` 查组件名、函数名、类型名。
3. 使用 `codegraph_context` 查询具体交互或接口流程。

回答要求：
- 引用 `.vue`、`.ts` 文件路径。
- 区分视图层、状态层、服务层和类型定义。
