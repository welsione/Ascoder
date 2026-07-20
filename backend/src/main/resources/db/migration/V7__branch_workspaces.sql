create table branchWorkspaces (
    id bigint primary key auto_increment,
    repositoryId bigint not null,
    branchName varchar(255) not null,
    commitSha varchar(64) not null,
    worktreePath text not null,
    codegraphIndexPath text not null,
    status varchar(32) not null default 'CREATED',
    lastIndexedAt datetime(6),
    lastIndexError text,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_branchWorkspaces_repository_branch unique (repositoryId, branchName),
    constraint fk_branchWorkspaces_repository foreign key (repositoryId) references repositories(id)
);

alter table questions
    add column branchWorkspaceId bigint null comment '关联的分支 workspace ID' after repositoryId,
    add column branchName varchar(255) null comment '回答基于的分支名' after role,
    add column commitSha varchar(64) null comment '回答基于的 commitSha' after branchName,
    add constraint fk_questions_branchWorkspace foreign key (branchWorkspaceId) references branchWorkspaces(id);
