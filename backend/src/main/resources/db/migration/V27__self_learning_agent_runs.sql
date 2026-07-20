create table learningAgentRuns (
    id bigint primary key auto_increment,
    projectSpaceId bigint not null,
    status varchar(32) not null,
    limitCount int not null default 12,
    createdInsightCount int not null default 0,
    consumedRawEventCount int not null default 0,
    skippedRawEventCount int not null default 0,
    currentRawEventIdsJson text,
    message text,
    errorMessage mediumtext,
    startedAt datetime(6),
    finishedAt datetime(6),
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint fk_learningAgentRuns_projectSpace foreign key (projectSpaceId) references projectSpaces(id)
);

create index idx_learningAgentRuns_space_created on learningAgentRuns(projectSpaceId, createdAt);
create index idx_learningAgentRuns_space_status on learningAgentRuns(projectSpaceId, status);
