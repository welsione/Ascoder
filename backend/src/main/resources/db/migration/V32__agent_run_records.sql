create table agentRunRecords (
    id bigint primary key auto_increment,
    agentId varchar(120) not null,
    agentConfigId bigint not null,
    questionId bigint,
    conversationId bigint,
    attemptNo int not null default 1,
    status varchar(32) not null,
    inputSummary text,
    outputSummary text,
    toolCallCount int not null default 0,
    iterCount int not null default 0,
    errorMessage mediumtext,
    startedAt datetime(6) not null,
    finishedAt datetime(6),
    durationMs bigint,
    createdAt datetime(6) not null default current_timestamp(6),
    constraint fk_agentRunRecords_agentConfig foreign key (agentConfigId) references agentConfigs(id)
);

create index idx_agentRunRecords_agentId_started on agentRunRecords(agentId, startedAt desc);
create index idx_agentRunRecords_questionId on agentRunRecords(questionId);
