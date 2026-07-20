-- 为 branchWorkspaces 和 projectSpaceMembers 添加 commitMessage 字段
-- 用于在 UI 中展示提交信息替代原始 SHA

alter table branchWorkspaces
    add column commitMessage varchar(512) null after commitSha;

alter table projectSpaceMembers
    add column commitMessage varchar(512) null after commitSha;
