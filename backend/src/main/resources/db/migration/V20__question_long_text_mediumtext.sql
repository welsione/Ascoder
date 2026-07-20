alter table questions
    modify column answer mediumtext null comment 'Agent 最终回答',
    modify column answerSummary mediumtext null comment '结构化回答摘要',
    modify column answerEvidenceJson mediumtext null comment '结构化代码证据列表，JSON 数组格式',
    modify column analysisProcess mediumtext null comment 'Agent 流式分析过程',
    modify column uncertainty mediumtext null comment '回答中的不确定性说明',
    modify column nextStep mediumtext null comment '建议下一步操作',
    modify column codegraphContext mediumtext null comment 'CodeGraph 上下文',
    modify column errorMessage mediumtext null comment '失败原因';
