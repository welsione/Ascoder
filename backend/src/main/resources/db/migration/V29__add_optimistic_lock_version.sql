alter table learningInsights add column version bigint not null default 0 after updatedAt;
alter table learningKnowledgeItems add column version bigint not null default 0 after updatedAt;
alter table learningAgentRuns add column version bigint not null default 0 after updatedAt;
alter table agentToolConfigs add column version bigint not null default 0 after updatedAt;
