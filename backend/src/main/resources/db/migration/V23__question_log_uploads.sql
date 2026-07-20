-- 将 questions.logUploadId 单值列替换为 logUploadIdsJson，支持多文件关联
ALTER TABLE questions ADD COLUMN logUploadIdsJson varchar(512) null AFTER branchWorkspaceId;

-- 迁移现有单值数据为 JSON 数组
UPDATE questions SET logUploadIdsJson = CONCAT('[', logUploadId, ']') WHERE logUploadId IS NOT NULL;

-- 删除旧列
ALTER TABLE questions DROP FOREIGN KEY fk_questions_logUpload;
ALTER TABLE questions DROP COLUMN logUploadId;
