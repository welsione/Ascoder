create table repositoryBranches (
    id bigint primary key auto_increment,
    repositoryId bigint not null,
    name varchar(255) not null,
    refName varchar(512) not null,
    commitSha varchar(64) not null,
    remoteName varchar(120),
    sourceKind varchar(32) not null,
    active bit not null default b'1',
    lastSeenAt datetime(6),
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_repositoryBranches_repository_refName unique (repositoryId, refName),
    constraint fk_repositoryBranches_repository foreign key (repositoryId) references repositories(id)
);

alter table projectSpaceMembers
    add column branchId bigint null after branchWorkspaceId,
    add column branchRefName varchar(512) null after branchName,
    add column branchSourceKind varchar(32) null after branchRefName,
    add constraint fk_projectSpaceMembers_branch foreign key (branchId) references repositoryBranches(id);
