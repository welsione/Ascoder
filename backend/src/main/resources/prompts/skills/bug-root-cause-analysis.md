# Bug Root Cause Analysis

适用场景：
- 用户询问报错、失败、异常、为什么没有生效。
- 需要定位可能根因和下一步验证命令。

推荐 CodeGraph 工具顺序：
1. 使用 `codegraph_context` 查询错误关键词、业务关键词和框架关键词。
2. 找到可疑符号后，用 `codegraph_callers` 查询调用入口。
3. 用 `codegraph_callees` 查看内部依赖和异常传播。

回答要求：
- 区分“已确认根因”和“候选原因”。
- 给出最小验证步骤。
- 如果证据不足，明确还需要日志、配置或复现输入。
