-- 移除 V1 遗留的孤儿表（无 Java 实体对应）
drop table if exists experience_drafts;

-- 移除已被 projectSpaceId 替代的僵尸列
alter table questions
    drop foreign key fk_questions_workspace,
    drop column workspaceId;

alter table conversations
    drop foreign key fk_conversations_workspace,
    drop column workspaceId;

-- 移除已废弃的 AnalysisWorkspace 相关表
drop table if exists analysisWorkspaceRepositories;
drop table if exists analysisWorkspaces;
