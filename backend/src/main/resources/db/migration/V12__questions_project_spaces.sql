alter table conversations
    modify column workspaceId bigint null,
    add column projectSpaceId bigint null after workspaceId,
    add constraint fk_conversations_projectSpace foreign key (projectSpaceId) references projectSpaces(id);

alter table questions
    modify column workspaceId bigint null,
    add column projectSpaceId bigint null after workspaceId,
    add constraint fk_questions_projectSpace foreign key (projectSpaceId) references projectSpaces(id);
