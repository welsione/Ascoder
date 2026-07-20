项目空间：{{projectSpaceName}}

请从下面的完整会话原始记录中整理一条候选洞察，供管理员审核。

重要：本次只处理这一组 conversation。请一次性返回结构化 SelfLearningInsightDraft，不要分多轮，不要解释过程。

## 整理目标

优先发现这些可沉淀内容：

1. 用户明确指出错误、修正回答、补充正确业务逻辑。
2. 业务术语与代码名词、类、方法、接口、配置项之间的对应关系。
3. 代码实现背后的业务专业语境，例如合同、签署、审批、支付、对账等领域语义。
4. 以后排查同类问题时有帮助的注意事项、触发条件、边界条件。
5. Git 证据可以追溯到提交、作者、提交说明的知识点。
6. 容易误解或不能直接复用的负面经验。

## 字段要求

- type: 根据内容选择 BUSINESS_CONTEXT、GLOSSARY、CODE_CONVENTION、TROUBLESHOOTING、ARCHITECTURE_DECISION、BUG_FIX、NEGATIVE_EXAMPLE、QUESTION_ANSWER、REQUIREMENT_LOGIC、TEST_CONSIDERATION。
- title: 像审核标题，直接说明这条候选洞察想沉淀什么。
- summary: 用一两句话说明为什么它可能对后续用户有用。
- conclusion: 写成可审核结论；证据不足时要说明“待验证”，不要伪装成已确认事实。
- businessContext: 提炼用户真实业务语境、触发场景、用户说法和专业语义。
- glossaryMappingsJson: 仅在确实出现代码名词/业务名词映射时输出 JSON 数组，否则留空。建议结构为 [{"term":"业务词","meaning":"代码含义或业务含义","codeSymbol":"类/方法/接口"}]。
- codeSymbolsJson: 仅输出原始记录中明确出现的代码符号 JSON 数组，不要编造。
- warnings: 写管理员审核注意事项，必须提醒核对当前代码、工具证据和 Git 证据。
- applicableScope: 写清适用项目、模块、业务场景和不适用边界。
- evidenceJson: 只整理原始记录已有证据，不要编造。可以保留 questionId、rawEventId、文件、接口、异常、工具摘要等。
- gitProvenanceJson: 只整理原始记录已有 Git 证据，不要编造。可以保留 commitSha、branch、author、commitMessage 等。
- tags: 输出逗号分隔标签，至少包含 llm-agent。
- confidence: 0.1 到 0.8；未核验前不要超过 0.65。

## 质量要求

1. 不要把完整回答复制成 conclusion。
2. 不要生成无法审核的大段散文。
3. 不要输出“用户问了什么、助手答了什么”的流水账。
4. 对管理员最有价值的是“可复用结论 + 证据位置 + 待核对风险”。
5. 如果洞察不适合审核通过，请在 warnings 中明确写出原因。
6. 字段保持短句，避免长篇总结导致超时。

## 原始记录

{{rawEventsText}}
