create table analysisWorkspaces (
    id bigint primary key auto_increment,
    name varchar(120) not null,
    description text,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_analysisWorkspaces_name unique (name)
);

create table analysisWorkspaceRepositories (
    id bigint primary key auto_increment,
    workspaceId bigint not null,
    repositoryId bigint not null,
    branchWorkspaceId bigint not null,
    role varchar(64) not null default 'repository',
    primaryRepository bit not null default b'0',
    createdAt datetime(6) not null default current_timestamp(6),
    constraint uk_analysisWorkspaceRepositories_workspace_branchWorkspace unique (workspaceId, branchWorkspaceId),
    constraint fk_analysisWorkspaceRepositories_workspace foreign key (workspaceId) references analysisWorkspaces(id),
    constraint fk_analysisWorkspaceRepositories_repository foreign key (repositoryId) references repositories(id),
    constraint fk_analysisWorkspaceRepositories_branchWorkspace foreign key (branchWorkspaceId) references branchWorkspaces(id)
);

insert into analysisWorkspaces (name, description, createdAt, updatedAt)
select concat(r.name, ' workspace'), 'Migrated single-repository workspace', r.createdAt, r.updatedAt
from repositories r
group by r.id, r.name, r.createdAt, r.updatedAt;

insert into analysisWorkspaceRepositories (
    workspaceId,
    repositoryId,
    branchWorkspaceId,
    role,
    primaryRepository,
    createdAt
)
select
    aw.id,
    bw.repositoryId,
    bw.id,
    'repository',
    b'1',
    bw.createdAt
from branchWorkspaces bw
join repositories r on r.id = bw.repositoryId
join analysisWorkspaces aw on aw.name = concat(r.name, ' workspace');

alter table conversations
    add column workspaceId bigint null after id,
    add constraint fk_conversations_workspace foreign key (workspaceId) references analysisWorkspaces(id);

alter table questions
    add column workspaceId bigint null after id,
    add constraint fk_questions_workspace foreign key (workspaceId) references analysisWorkspaces(id);

update conversations c
join analysisWorkspaceRepositories awr
  on awr.branchWorkspaceId = c.branchWorkspaceId
set c.workspaceId = awr.workspaceId
where c.workspaceId is null;

update conversations c
join repositories r on r.id = c.repositoryId
join analysisWorkspaces aw on aw.name = concat(r.name, ' workspace')
set c.workspaceId = aw.id
where c.workspaceId is null;

update questions q
join analysisWorkspaceRepositories awr
  on awr.branchWorkspaceId = q.branchWorkspaceId
set q.workspaceId = awr.workspaceId
where q.workspaceId is null;

update questions q
join repositories r on r.id = q.repositoryId
join analysisWorkspaces aw on aw.name = concat(r.name, ' workspace')
set q.workspaceId = aw.id
where q.workspaceId is null;

alter table conversations
    modify column workspaceId bigint not null,
    modify column repositoryId bigint null;

alter table questions
    modify column workspaceId bigint not null,
    modify column repositoryId bigint null;
