# Impact Analysis

适用场景：
- 用户询问“改这个会影响哪里”、“哪些测试需要跑”、“这个文件变更影响什么”。
- 需要评估调用方、下游依赖和测试覆盖。

推荐 CodeGraph 工具顺序：
1. 已知符号时先用 `codegraph_impact`。
2. 已知文件时使用 `codegraph_affected`。
3. 对关键符号补充 `codegraph_callers`。

回答要求：
- 输出影响范围、风险等级、建议验证命令。
- 标明直接影响和推断影响。
