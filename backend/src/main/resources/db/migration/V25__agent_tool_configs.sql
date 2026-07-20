create table agentToolConfigs (
    id bigint primary key auto_increment,
    toolKey varchar(120) not null,
    displayName varchar(120) not null,
    groupName varchar(80) not null,
    riskLevel varchar(40) not null,
    description text not null,
    enabled boolean not null default true,
    builtin boolean not null default true,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_agentToolConfigs_toolKey unique (toolKey)
);
