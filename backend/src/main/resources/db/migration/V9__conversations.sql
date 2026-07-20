create table conversations (
    id bigint primary key auto_increment,
    repositoryId bigint not null,
    branchWorkspaceId bigint null,
    title varchar(200) not null,
    role varchar(64),
    branchName varchar(255),
    commitSha varchar(64),
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint fk_conversations_repository foreign key (repositoryId) references repositories(id),
    constraint fk_conversations_branchWorkspace foreign key (branchWorkspaceId) references branchWorkspaces(id)
);

alter table questions
    add column conversationId bigint null after id,
    add constraint fk_questions_conversation foreign key (conversationId) references conversations(id);

insert into conversations (
    repositoryId,
    branchWorkspaceId,
    title,
    role,
    branchName,
    commitSha,
    createdAt,
    updatedAt
)
select
    repositoryId,
    branchWorkspaceId,
    left(replace(replace(question, '\r', ' '), '\n', ' '), 200),
    role,
    branchName,
    commitSha,
    createdAt,
    createdAt
from questions
where conversationId is null;

update questions q
join conversations c
  on c.repositoryId = q.repositoryId
 and c.createdAt = q.createdAt
 and c.title = left(replace(replace(q.question, '\r', ' '), '\n', ' '), 200)
set q.conversationId = c.id
where q.conversationId is null;
