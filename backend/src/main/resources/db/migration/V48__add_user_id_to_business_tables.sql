-- 数据隔离：给业务表添加 userId 列，实现用户级数据隔离
-- userId = NULL 表示系统级/公共资源（已有数据），所有用户可见
-- 普通用户只能看到 userId = 自己 或 userId IS NULL 的资源，管理员可看所有

-- 项目空间：归属用户
ALTER TABLE projectSpaces ADD COLUMN userId BIGINT NULL;
ALTER TABLE projectSpaces ADD CONSTRAINT fk_projectSpaces_user
    FOREIGN KEY (userId) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_projectSpaces_userId ON projectSpaces(userId);

-- 会话：归属用户
ALTER TABLE conversations ADD COLUMN userId BIGINT NULL;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_user
    FOREIGN KEY (userId) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_conversations_userId ON conversations(userId);

-- 项目：归属用户
ALTER TABLE projects ADD COLUMN userId BIGINT NULL;
ALTER TABLE projects ADD CONSTRAINT fk_projects_user
    FOREIGN KEY (userId) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_projects_userId ON projects(userId);

-- 日志上传：归属用户
ALTER TABLE logUploads ADD COLUMN userId BIGINT NULL;
ALTER TABLE logUploads ADD CONSTRAINT fk_logUploads_user
    FOREIGN KEY (userId) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_logUploads_userId ON logUploads(userId);

-- 异步任务：归属用户
ALTER TABLE asyncTasks ADD COLUMN userId BIGINT NULL;
ALTER TABLE asyncTasks ADD CONSTRAINT fk_asyncTasks_user
    FOREIGN KEY (userId) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_asyncTasks_userId ON asyncTasks(userId);
