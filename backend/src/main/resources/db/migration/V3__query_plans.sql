create table queryPlans (
    id bigint primary key auto_increment,
    questionId bigint not null,
    questionType varchar(48) not null,
    rewrittenQueriesJson text not null,
    recommendedToolsJson text not null,
    reasoning text,
    createdAt datetime(6) not null default current_timestamp(6),
    constraint uk_queryPlans_question unique (questionId),
    constraint fk_queryPlans_question foreign key (questionId) references questions(id)
);