alter table repositories
    add column authUsername varchar(255) null comment 'Git HTTPS 认证用户名' after defaultBranch,
    add column authPassword text null comment 'Git HTTPS 认证密码' after authUsername;
