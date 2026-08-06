-- Self Learning Agent 整理失败的原始记录累计失败次数，达到上限后跳过，避免反复重试消耗 token
alter table learningRawEvents
    add column failedCount int not null default 0;
