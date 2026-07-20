create table agent_skills (
    id bigint primary key auto_increment,
    name varchar(120) not null,
    description text not null,
    skillContent longtext not null,
    source varchar(120) not null default 'manual',
    enabled boolean not null default true,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_agent_skills_name unique (name)
);

create table mcp_servers (
    id bigint primary key auto_increment,
    name varchar(120) not null,
    description text,
    transport varchar(32) not null,
    command text,
    argumentsJson text,
    endpointUrl text,
    headersJson text,
    queryParamsJson text,
    enabledToolsJson text,
    disabledToolsJson text,
    timeoutSeconds int not null default 30,
    enabled boolean not null default false,
    lastError text,
    createdAt datetime(6) not null default current_timestamp(6),
    updatedAt datetime(6) not null default current_timestamp(6),
    constraint uk_mcp_servers_name unique (name)
);
