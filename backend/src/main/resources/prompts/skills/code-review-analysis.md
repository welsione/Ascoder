# Code Review Analysis

适用场景：
- 用户要求 review、检查风险、判断实现是否合理。
- 分析某段逻辑的潜在缺陷、并发风险、异常处理和测试缺口。

推荐 CodeGraph 工具顺序：
1. 使用 `codegraph_context` 获取相关实现。
2. 已知符号时使用 `codegraph_callers` 和 `codegraph_callees` 交叉验证影响。
3. 涉及改动影响时使用 `codegraph_impact` 或 `codegraph_affected`。

回答要求：
- 先列问题和风险，再给总结。
- 每个结论尽量绑定文件路径、符号或行号。
- 明确哪些点已经验证，哪些只是推断。
