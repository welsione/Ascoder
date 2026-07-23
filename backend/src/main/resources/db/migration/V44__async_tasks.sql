create table asyncTasks (
    id              bigint primary key auto_increment,
    kind            varchar(64) not null,
    status          varchar(32) not null default 'QUEUED',
    businessId      bigint,
    contextJson     mediumtext,
    progress        int not null default -1,
    statusMessage   text,
    errorMessage    mediumtext,
    resultJson      mediumtext,
    maxRetries      int not null default 0,
    retryCount      int not null default 0,
    timeoutMs       bigint not null default 0,
    queuedAt        datetime(6) not null default current_timestamp(6),
    startedAt       datetime(6),
    finishedAt      datetime(6),
    createdAt       datetime(6) not null default current_timestamp(6),
    updatedAt       datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
    version         bigint not null default 0
);

create index idx_asyncTasks_kind_status on asyncTasks(kind, status);
create index idx_asyncTasks_businessId on asyncTasks(businessId);
create index idx_asyncTasks_status on asyncTasks(status);
