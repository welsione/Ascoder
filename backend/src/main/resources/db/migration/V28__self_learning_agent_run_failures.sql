alter table learningAgentRuns
    add column failedConversationCount int not null default 0,
    add column failureDetailsJson mediumtext;
