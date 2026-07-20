create table projects (
    id bigint primary key auto_increment,
    name varchar(120) not null,
    description text,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_projects_name unique (name)
);

create table projectRepositories (
    id bigint primary key auto_increment,
    projectId bigint not null,
    repositoryId bigint not null,
    alias varchar(120) not null,
    role varchar(64) not null default 'repository',
    primaryRepository bit not null default b'0',
    sortOrder int not null default 0,
    createdAt datetime(6) not null default current_timestamp(6),
    constraint uk_projectRepositories_project_repository unique (projectId, repositoryId),
    constraint uk_projectRepositories_project_alias unique (projectId, alias),
    constraint fk_projectRepositories_project foreign key (projectId) references projects(id),
    constraint fk_projectRepositories_repository foreign key (repositoryId) references repositories(id)
);

create table projectSpaces (
    id bigint primary key auto_increment,
    projectId bigint not null,
    name varchar(120) not null,
    description text,
    rootPath text not null,
    codegraphIndexPath text,
    status varchar(32) not null,
    lastPreparedAt datetime(6),
    lastIndexedAt datetime(6),
    lastError text,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_projectSpaces_project_name unique (projectId, name),
    constraint fk_projectSpaces_project foreign key (projectId) references projects(id)
);

create table projectSpaceMembers (
    id bigint primary key auto_increment,
    projectSpaceId bigint not null,
    repositoryId bigint not null,
    branchWorkspaceId bigint null,
    branchName varchar(255) not null,
    alias varchar(120) not null,
    role varchar(64) not null default 'repository',
    commitSha varchar(64),
    linkPath text,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_projectSpaceMembers_space_repository unique (projectSpaceId, repositoryId),
    constraint uk_projectSpaceMembers_space_alias unique (projectSpaceId, alias),
    constraint fk_projectSpaceMembers_space foreign key (projectSpaceId) references projectSpaces(id),
    constraint fk_projectSpaceMembers_repository foreign key (repositoryId) references repositories(id),
    constraint fk_projectSpaceMembers_branchWorkspace foreign key (branchWorkspaceId) references branchWorkspaces(id)
);
