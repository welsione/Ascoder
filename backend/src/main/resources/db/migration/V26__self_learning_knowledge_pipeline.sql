alter table selfLearningSettings
    add column rawEventCaptureEnabled bit not null default b'1',
    add column autoInsightEnabled bit not null default b'0',
    add column adminReviewRequired bit not null default b'1';

create table learningRawEvents (
    id bigint primary key auto_increment,
    projectSpaceId bigint not null,
    repositoryId bigint,
    branchName varchar(160),
    questionId bigint,
    conversationId bigint,
    agentId varchar(80),
    eventType varchar(48) not null,
    eventPayloadJson mediumtext,
    summary mediumtext,
    evidenceJson mediumtext,
    gitProvenanceJson mediumtext,
    userFeedbackType varchar(48),
    sourceCreatedAt datetime(6),
    createdAt datetime(6) not null default current_timestamp(6),
    constraint fk_learningRawEvents_projectSpace foreign key (projectSpaceId) references projectSpaces(id),
    constraint fk_learningRawEvents_repository foreign key (repositoryId) references repositories(id),
    constraint fk_learningRawEvents_question foreign key (questionId) references questions(id)
);

create index idx_learningRawEvents_space_type on learningRawEvents(projectSpaceId, eventType);
create index idx_learningRawEvents_space_created on learningRawEvents(projectSpaceId, createdAt);
create index idx_learningRawEvents_question on learningRawEvents(questionId);

create table learningInsights (
    id bigint primary key auto_increment,
    projectSpaceId bigint not null,
    repositoryId bigint,
    sourceRawEventIdsJson text,
    sourceQuestionIdsJson text,
    type varchar(48) not null,
    status varchar(32) not null,
    title varchar(200) not null,
    summary mediumtext,
    conclusion mediumtext not null,
    businessContext mediumtext,
    glossaryMappingsJson mediumtext,
    codeSymbolsJson mediumtext,
    warnings mediumtext,
    applicableScope text,
    evidenceJson mediumtext,
    gitProvenanceJson mediumtext,
    tags varchar(500),
    confidence double not null default 0,
    reviewerId bigint,
    reviewComment mediumtext,
    reviewedAt datetime(6),
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint fk_learningInsights_projectSpace foreign key (projectSpaceId) references projectSpaces(id),
    constraint fk_learningInsights_repository foreign key (repositoryId) references repositories(id)
);

create index idx_learningInsights_space_status on learningInsights(projectSpaceId, status);
create index idx_learningInsights_space_type on learningInsights(projectSpaceId, type);

create table learningKnowledgeItems (
    id bigint primary key auto_increment,
    projectSpaceId bigint not null,
    repositoryId bigint,
    sourceInsightIdsJson text,
    sourceRawEventIdsJson text,
    type varchar(48) not null,
    status varchar(32) not null,
    title varchar(200) not null,
    content mediumtext not null,
    summary mediumtext,
    applicableScope text,
    evidenceJson mediumtext,
    gitProvenanceJson mediumtext,
    tags varchar(500),
    confidence double not null default 0,
    usageCount int not null default 0,
    acceptedCount int not null default 0,
    rejectedCount int not null default 0,
    lastUsedAt datetime(6),
    staleReason text,
    reviewerId bigint,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint fk_learningKnowledgeItems_projectSpace foreign key (projectSpaceId) references projectSpaces(id),
    constraint fk_learningKnowledgeItems_repository foreign key (repositoryId) references repositories(id)
);

create index idx_learningKnowledgeItems_space_status on learningKnowledgeItems(projectSpaceId, status);
create index idx_learningKnowledgeItems_space_type on learningKnowledgeItems(projectSpaceId, type);

create table learningKnowledgeRelations (
    id bigint primary key auto_increment,
    projectSpaceId bigint not null,
    sourceKnowledgeItemId bigint,
    targetKnowledgeItemId bigint,
    relationType varchar(64) not null,
    sourceRefType varchar(64),
    sourceRefValue varchar(500),
    targetRefType varchar(64),
    targetRefValue varchar(500),
    description text,
    confidence double not null default 0,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint fk_learningKnowledgeRelations_projectSpace foreign key (projectSpaceId) references projectSpaces(id),
    constraint fk_learningKnowledgeRelations_sourceKnowledge foreign key (sourceKnowledgeItemId) references learningKnowledgeItems(id),
    constraint fk_learningKnowledgeRelations_targetKnowledge foreign key (targetKnowledgeItemId) references learningKnowledgeItems(id)
);
