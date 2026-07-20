alter table repositories
    add column remoteUrl text null comment '远程 Git 仓库地址' after localPath,
    add column defaultBranch varchar(255) null comment '默认分支' after remoteUrl,
    add column lastPulledAt datetime(6) null comment '最近一次拉取时间' after lastIndexedAt,
    add column lastPullError text null comment '最近一次拉取错误' after lastIndexError;
