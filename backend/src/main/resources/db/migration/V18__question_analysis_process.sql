alter table questions
    add column analysisProcess text null comment 'Agent 流式分析过程' after answerEvidenceJson;
