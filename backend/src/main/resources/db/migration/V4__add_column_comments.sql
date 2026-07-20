alter table repositories
    modify column id bigint not null auto_increment comment '主键ID',
    modify column name varchar(120) not null comment '仓库名称',
    modify column localPath text not null comment '本地仓库路径',
    modify column status varchar(32) not null default 'CREATED' comment '仓库状态：CREATED/INDEXING/READY/FAILED',
    modify column lastIndexedAt datetime(6) null comment '最近一次索引完成时间',
    modify column lastIndexError text null comment '最近一次索引失败错误信息',
    modify column createdAt datetime(6) not null default current_timestamp(6) comment '创建时间',
    modify column updatedAt datetime(6) not null default current_timestamp(6) comment '更新时间';

alter table questions
    modify column id bigint not null auto_increment comment '主键ID',
    modify column repositoryId bigint not null comment '关联的仓库ID',
    modify column question text not null comment '用户提出的问题',
    modify column role varchar(64) null comment '提问者角色，如 developer/project_manager/customer_support',
    modify column status varchar(32) not null default 'PENDING' comment '问题状态：PENDING/RUNNING/SUCCEEDED/FAILED',
    modify column answer text null comment 'Agent 生成的 Markdown 回答',
    modify column codegraphContext text null comment 'CodeGraph 工具返回的代码上下文',
    modify column errorMessage text null comment '失败时的错误信息',
    modify column startedAt datetime(6) null comment 'Agent 开始处理时间',
    modify column completedAt datetime(6) null comment 'Agent 处理完成时间',
    modify column createdAt datetime(6) not null default current_timestamp(6) comment '创建时间';

alter table experience_drafts
    modify column id bigint not null auto_increment comment '主键ID',
    modify column repositoryId bigint not null comment '关联的仓库ID',
    modify column questionId bigint null comment '关联的问题ID',
    modify column title varchar(200) not null comment '经验草稿标题',
    modify column summary text not null comment '经验草稿摘要',
    modify column codeReferences text null comment '代码引用，JSON 或文本格式',
    modify column createdAt datetime(6) not null default current_timestamp(6) comment '创建时间';

alter table agent_skills
    modify column id bigint not null auto_increment comment '主键ID',
    modify column name varchar(120) not null comment 'Skill 名称',
    modify column description text not null comment 'Skill 描述',
    modify column skillContent longtext not null comment 'Skill 内容，Markdown 格式的知识和指令',
    modify column source varchar(120) not null default 'manual' comment 'Skill 来源：manual/file/url',
    modify column enabled boolean not null default true comment '是否启用',
    modify column createdAt datetime(6) not null default current_timestamp(6) comment '创建时间',
    modify column updatedAt datetime(6) not null default current_timestamp(6) comment '更新时间';

alter table mcp_servers
    modify column id bigint not null auto_increment comment '主键ID',
    modify column name varchar(120) not null comment 'MCP Server 名称',
    modify column description text null comment 'MCP Server 描述',
    modify column transport varchar(32) not null comment '传输协议：STDIO/SSE/HTTP',
    modify column command text null comment 'STDIO 模式的启动命令',
    modify column argumentsJson text null comment 'STDIO 模式的命令参数，JSON 数组格式',
    modify column endpointUrl text null comment 'HTTP/SSE 模式的服务端点 URL',
    modify column headersJson text null comment 'HTTP 请求头，JSON 对象格式',
    modify column queryParamsJson text null comment 'URL 查询参数，JSON 对象格式',
    modify column enabledToolsJson text null comment '启用的工具白名单，JSON 数组格式',
    modify column disabledToolsJson text null comment '禁用的工具黑名单，JSON 数组格式',
    modify column timeoutSeconds int not null default 30 comment '超时时间，单位秒',
    modify column enabled boolean not null default false comment '是否启用',
    modify column lastError text null comment '最近一次注册失败的错误信息',
    modify column createdAt datetime(6) not null default current_timestamp(6) comment '创建时间',
    modify column updatedAt datetime(6) not null default current_timestamp(6) comment '更新时间';

alter table queryPlans
    modify column id bigint not null auto_increment comment '主键ID',
    modify column questionId bigint not null comment '关联的问题ID',
    modify column questionType varchar(48) not null comment '问题类型：ENTRY_POINT/BUSINESS_FLOW/CALLER_ANALYSIS 等',
    modify column rewrittenQueriesJson text not null comment '改写后的查询列表，JSON 数组格式',
    modify column recommendedToolsJson text not null comment '推荐使用的工具列表，JSON 数组格式',
    modify column reasoning text null comment '分类推理说明',
    modify column createdAt datetime(6) not null default current_timestamp(6) comment '创建时间';

alter table repositories comment '代码仓库表，记录被索引的代码仓库信息';
alter table questions comment '问题表，记录用户提交的代码问题和 Agent 回答';
alter table experience_drafts comment '经验草稿表，记录从问答中提取的经验知识';
alter table agent_skills comment 'Agent Skill 配置表，存储可注入 Agent 的技能内容';
alter table mcp_servers comment 'MCP Server 配置表，存储外部 MCP 工具服务器的连接信息';
alter table queryPlans comment '查询规划表，记录问题分类、改写查询和推荐工具';
