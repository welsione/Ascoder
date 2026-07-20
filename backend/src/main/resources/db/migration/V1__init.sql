create table repositories (
    id bigint primary key auto_increment,
    name varchar(120) not null,
    localPath text not null,
    status varchar(32) not null default 'CREATED',
    lastIndexedAt datetime(6),
    lastIndexError text,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_repositories_name unique (name)
);

create table questions (
    id bigint primary key auto_increment,
    repositoryId bigint not null,
    question text not null,
    role varchar(64),
    status varchar(32) not null default 'PENDING',
    answer text,
    codegraphContext text,
    errorMessage text,
    startedAt datetime(6),
    completedAt datetime(6),
    createdAt datetime(6) not null default current_timestamp(6),
    constraint fk_questions_repository foreign key (repositoryId) references repositories(id)
);

create table experience_drafts (
    id bigint primary key auto_increment,
    repositoryId bigint not null,
    questionId bigint,
    title varchar(200) not null,
    summary text not null,
    codeReferences text,
    createdAt datetime(6) not null default current_timestamp(6),
    constraint fk_experience_drafts_repository foreign key (repositoryId) references repositories(id),
    constraint fk_experience_drafts_question foreign key (questionId) references questions(id)
);
