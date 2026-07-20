alter table questions
    add column answerSummary text null comment '结构化回答摘要' after answer,
    add column answerEvidenceJson text null comment '结构化代码证据列表，JSON 数组格式' after answerSummary,
    add column uncertainty text null comment '回答中的不确定性说明' after answerEvidenceJson,
    add column nextStep text null comment '建议下一步操作' after uncertainty;
