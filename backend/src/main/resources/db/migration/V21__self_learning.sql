create table selfLearningSettings (
    id bigint primary key auto_increment,
    projectSpaceId bigint not null,
    enabled bit not null default b'0',
    autoCandidateEnabled bit not null default b'0',
    answerInjectionEnabled bit not null default b'0',
    sourceVisibleEnabled bit not null default b'1',
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_selfLearningSettings_projectSpace unique (projectSpaceId),
    constraint fk_selfLearningSettings_projectSpace foreign key (projectSpaceId) references projectSpaces(id)
);

create table learningExperiences (
    id bigint primary key auto_increment,
    projectSpaceId bigint not null,
    repositoryId bigint,
    sourceQuestionId bigint,
    type varchar(48) not null,
    status varchar(32) not null,
    title varchar(200) not null,
    problem mediumtext,
    conclusion mediumtext not null,
    applicableScope text,
    evidenceJson mediumtext,
    gitProvenanceJson mediumtext,
    tags varchar(500),
    confidence double not null default 0,
    usageCount int not null default 0,
    acceptedCount int not null default 0,
    rejectedCount int not null default 0,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint fk_learningExperiences_projectSpace foreign key (projectSpaceId) references projectSpaces(id),
    constraint fk_learningExperiences_repository foreign key (repositoryId) references repositories(id),
    constraint fk_learningExperiences_sourceQuestion foreign key (sourceQuestionId) references questions(id)
);

create index idx_learningExperiences_space_status on learningExperiences(projectSpaceId, status);
create index idx_learningExperiences_space_type on learningExperiences(projectSpaceId, type);

create table learningTerms (
    id bigint primary key auto_increment,
    projectSpaceId bigint not null,
    term varchar(160) not null,
    aliasesJson text,
    definition mediumtext not null,
    scope text,
    examples mediumtext,
    source varchar(64) not null default 'manual',
    confidence double not null default 0,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_learningTerms_space_term unique (projectSpaceId, term),
    constraint fk_learningTerms_projectSpace foreign key (projectSpaceId) references projectSpaces(id)
);

create table learningCorrections (
    id bigint primary key auto_increment,
    projectSpaceId bigint not null,
    sourceQuestionId bigint,
    wrongConclusion mediumtext not null,
    correctedConclusion mediumtext not null,
    verificationProcess mediumtext,
    evidenceJson mediumtext,
    status varchar(32) not null,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint fk_learningCorrections_projectSpace foreign key (projectSpaceId) references projectSpaces(id),
    constraint fk_learningCorrections_sourceQuestion foreign key (sourceQuestionId) references questions(id)
);

create index idx_learningCorrections_space_status on learningCorrections(projectSpaceId, status);
