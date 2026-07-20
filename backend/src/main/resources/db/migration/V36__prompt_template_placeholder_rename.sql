-- T06b 补充：更新 orchestrator 的 taskTemplate 占位符
-- 原因：TaskPromptContext.answerStyle 从 AnswerStyle 对象改为扁平 String 字段，
--       模板中 {{answerStyle.instruction}} 和 {{answerStyle.roleKey}} 需改为
--       {{answerStyleInstruction}} 和 {{answerStyleRoleKey}}

UPDATE agentConfigs SET taskTemplate = REPLACE(
    REPLACE(taskTemplate, '{{answerStyle.roleKey}}', '{{answerStyleRoleKey}}'),
    '{{answerStyle.instruction}}', '{{answerStyleInstruction}}'
), updatedAt = CURRENT_TIMESTAMP(6)
WHERE agentId = 'orchestrator';
