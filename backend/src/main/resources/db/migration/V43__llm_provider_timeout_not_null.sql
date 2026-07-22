-- 修复 llmProvider.timeoutSeconds 允许 NULL 导致 merge() NPE 的问题：
-- 已有 NULL 行回填默认值 240s（与 agent.tool-timeout-seconds 默认值一致），列改为 NOT NULL。
UPDATE llmProvider SET timeoutSeconds = 240 WHERE timeoutSeconds IS NULL;
ALTER TABLE llmProvider MODIFY COLUMN timeoutSeconds BIGINT NOT NULL DEFAULT 240;
