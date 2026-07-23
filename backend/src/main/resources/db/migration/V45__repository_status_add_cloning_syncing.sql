-- 扩展 RepositoryStatus 枚举，新增 CLONING 和 SYNCING 中间态。
-- CodeRepository 实体表名为 repositories（见 @Table(name="repositories")）。
-- MySQL ALTER ENUM 需要重新声明完整列表。
ALTER TABLE repositories MODIFY COLUMN status ENUM('CREATED','CLONING','SYNCING','INDEXING','READY','FAILED') NOT NULL DEFAULT 'CREATED';
