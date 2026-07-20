create table logUploads (
    id              bigint auto_increment primary key,
    projectSpaceId  bigint       not null,
    createdBy       varchar(128) null,
    originalFilename varchar(512) not null,
    storedPath      varchar(1024) not null,
    fileType        varchar(32)  not null,
    fileSize        bigint       not null,
    status          varchar(32)  not null,
    errorMessage    text         null,
    summaryJson     mediumtext   null,
    createdAt       datetime(3)  not null default current_timestamp(3),
    expiresAt       datetime(3)  null,
    constraint fk_logUploads_projectSpace foreign key (projectSpaceId) references projectSpaces(id)
) engine=innodb default charset=utf8mb4;

create index idx_logUploads_projectSpace_createdAt on logUploads(projectSpaceId, createdAt desc);

create table logFiles (
    id           bigint auto_increment primary key,
    uploadId     bigint        not null,
    displayName  varchar(512)  not null,
    storedPath   varchar(1024) not null,
    fileSize     bigint        not null,
    lineCount    bigint        null,
    startedAt    datetime(3)   null,
    endedAt      datetime(3)   null,
    parseStatus  varchar(32)   not null,
    limitedMode  bit(1)        not null default b'0',
    summaryJson  mediumtext    null,
    createdAt    datetime(3)   not null default current_timestamp(3),
    constraint fk_logFiles_upload foreign key (uploadId) references logUploads(id) on delete cascade
) engine=innodb default charset=utf8mb4;

create index idx_logFiles_upload on logFiles(uploadId);

create table logAnalysisTasks (
    id             bigint auto_increment primary key,
    questionId     bigint       not null,
    projectSpaceId bigint       not null,
    uploadId       bigint       not null,
    status         varchar(32)  not null,
    summaryJson    mediumtext   null,
    resultJson     mediumtext   null,
    createdAt      datetime(3)  not null default current_timestamp(3),
    completedAt    datetime(3)  null,
    constraint fk_logAnalysisTasks_question foreign key (questionId) references questions(id) on delete cascade,
    constraint fk_logAnalysisTasks_upload foreign key (uploadId) references logUploads(id)
) engine=innodb default charset=utf8mb4;

create index idx_logAnalysisTasks_question on logAnalysisTasks(questionId);

create table logEvidenceRefs (
    id            bigint auto_increment primary key,
    taskId        bigint        not null,
    logFileId     bigint        not null,
    lineStart     int           not null,
    lineEnd       int           not null,
    snippet       text          null,
    maskedSnippet text          null,
    evidenceType  varchar(32)   null,
    createdAt     datetime(3)   not null default current_timestamp(3),
    constraint fk_logEvidenceRefs_task foreign key (taskId) references logAnalysisTasks(id) on delete cascade,
    constraint fk_logEvidenceRefs_file foreign key (logFileId) references logFiles(id) on delete cascade
) engine=innodb default charset=utf8mb4;

alter table questions
    add column logUploadId bigint null after branchWorkspaceId,
    add constraint fk_questions_logUpload foreign key (logUploadId) references logUploads(id);
