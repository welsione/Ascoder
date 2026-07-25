-- 用户表
CREATE TABLE users (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(80)  NOT NULL,
    password         VARCHAR(200) NOT NULL COMMENT 'BCrypt encrypted',
    nickname         VARCHAR(64)  NULL,
    email            VARCHAR(120) NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    accountNonLocked BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '防暴力破解锁定标记',
    loginFailCount   INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lastLoginAt      DATETIME     NULL,
    createdAt        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email)
);

-- 角色表
CREATE TABLE roles (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL COMMENT '角色编码',
    name        VARCHAR(100) NOT NULL COMMENT '角色显示名',
    description VARCHAR(500) NULL,
    builtin     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '内置角色不可删除',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    createdAt   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_roles_code UNIQUE (code)
);

-- 权限表
CREATE TABLE permissions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(100) NOT NULL COMMENT '权限编码',
    name        VARCHAR(200) NOT NULL COMMENT '权限显示名',
    module      VARCHAR(50)  NOT NULL COMMENT '所属模块',
    description VARCHAR(500) NULL,
    builtin     BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '内置权限不可删除',
    createdAt   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_permissions_code UNIQUE (code)
);

-- 用户-角色关联表
CREATE TABLE userRoles (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    userId    BIGINT NOT NULL,
    roleId    BIGINT NOT NULL,
    createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_userRoles_user_role UNIQUE (userId, roleId),
    CONSTRAINT fk_userRoles_user FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_userRoles_role FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE
);

-- 角色-权限关联表
CREATE TABLE rolePermissions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    roleId       BIGINT NOT NULL,
    permissionId BIGINT NOT NULL,
    createdAt    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_rolePermissions_role_permission UNIQUE (roleId, permissionId),
    CONSTRAINT fk_rolePermissions_role       FOREIGN KEY (roleId)       REFERENCES roles(id)       ON DELETE CASCADE,
    CONSTRAINT fk_rolePermissions_permission FOREIGN KEY (permissionId) REFERENCES permissions(id) ON DELETE CASCADE
);

-- Refresh Token 表
CREATE TABLE refreshTokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    userId      BIGINT       NOT NULL,
    tokenHash   VARCHAR(128) NOT NULL COMMENT 'SHA-256(token)',
    expiresAt   DATETIME     NOT NULL,
    revokedAt   DATETIME     NULL COMMENT '非空表示已吊销',
    userAgent   VARCHAR(500) NULL COMMENT '设备/浏览器标识',
    createdAt   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refreshTokens_user FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_refreshTokens_tokenHash (tokenHash),
    INDEX idx_refreshTokens_userId (userId)
);

-- 内置角色
INSERT INTO roles (code, name, description, builtin) VALUES
    ('ADMIN', '管理员',   '系统管理员，拥有所有权限', TRUE),
    ('USER',  '普通用户', '拥有基本操作权限',       TRUE);

-- 内置权限
INSERT INTO permissions (code, name, module, description) VALUES
    ('SYSTEM_SETTINGS:READ',  '查看系统设置',    'SYSTEM', '查看运行时设置'),
    ('SYSTEM_SETTINGS:WRITE', '修改系统设置',    'SYSTEM', '修改运行时设置'),
    ('LLM_PROVIDER:MANAGE',   '管理 LLM 供应商', 'SYSTEM', '创建/编辑/删除 LLM 供应商'),
    ('USER:MANAGE',           '管理用户',        'SYSTEM', '创建/编辑/启停/锁定用户'),
    ('ROLE:MANAGE',           '管理角色权限',    'SYSTEM', '创建/编辑角色和分配权限'),
    ('PROJECT:CREATE', '创建项目',     'PROJECT', '创建新项目'),
    ('PROJECT:READ',   '查看项目',     'PROJECT', '查看项目列表和详情'),
    ('PROJECT:WRITE',  '修改项目',     'PROJECT', '编辑项目信息'),
    ('PROJECT:DELETE', '删除项目',     'PROJECT', '删除项目'),
    ('REPOSITORY:CREATE', '创建仓库',     'REPOSITORY', '添加新仓库'),
    ('REPOSITORY:READ',   '查看仓库',     'REPOSITORY', '查看仓库列表'),
    ('REPOSITORY:MANAGE', '管理仓库',     'REPOSITORY', '仓库索引/拉取/删除等'),
    ('WORKSPACE:MANAGE',  '管理工作区',   'REPOSITORY', '分支工作区管理'),
    ('PROJECT_SPACE:CREATE', '创建项目空间', 'REPOSITORY', '创建新项目空间'),
    ('PROJECT_SPACE:READ',   '查看项目空间', 'REPOSITORY', '查看项目空间列表'),
    ('PROJECT_SPACE:MANAGE', '管理项目空间', 'REPOSITORY', '项目空间准备/索引/删除'),
    ('QUESTION:ASK',         '提交问题',     'CHAT', '向 Agent 提交代码问题'),
    ('QUESTION:READ',        '查看问题',     'CHAT', '查看问题列表和详情'),
    ('CONVERSATION:MANAGE',  '管理会话',     'CHAT', '删除会话'),
    ('AGENT_CONFIG:READ',  '查看 Agent 配置', 'AGENT', '查看 Agent 配置列表'),
    ('AGENT_CONFIG:MANAGE','管理 Agent 配置', 'AGENT', '创建/编辑/删除 Agent 配置'),
    ('SKILL:READ',         '查看技能',       'AGENT', '查看技能列表'),
    ('SKILL:MANAGE',       '管理技能',       'AGENT', '创建/编辑/启停技能'),
    ('TOOL:READ',          '查看工具',       'AGENT', '查看工具列表'),
    ('TOOL:MANAGE',        '管理工具',       'AGENT', '启停工具'),
    ('MCP_SERVER:READ',    '查看 MCP 服务',  'AGENT', '查看 MCP 服务器列表'),
    ('MCP_SERVER:MANAGE',  '管理 MCP 服务',  'AGENT', '创建/启停 MCP 服务器'),
    ('LOG_UPLOAD:CREATE',  '上传日志',       'AGENT', '上传日志文件'),
    ('LOG_UPLOAD:READ',    '查看日志上传',   'AGENT', '查看日志上传记录'),
    ('SELF_LEARNING:READ', '查看自学习',     'AGENT', '查看自学习洞察/知识/经验'),
    ('SELF_LEARNING:MANAGE','管理自学习',    'AGENT', '管理自学习设置/审批/触发');

-- ADMIN 拥有所有权限
INSERT INTO rolePermissions (roleId, permissionId)
    SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';

-- USER 拥有基本操作权限
INSERT INTO rolePermissions (roleId, permissionId)
    SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
    WHERE r.code = 'USER' AND p.code IN (
        'PROJECT:CREATE', 'PROJECT:READ', 'PROJECT:WRITE', 'PROJECT:DELETE',
        'REPOSITORY:CREATE', 'REPOSITORY:READ', 'REPOSITORY:MANAGE',
        'WORKSPACE:MANAGE',
        'PROJECT_SPACE:CREATE', 'PROJECT_SPACE:READ', 'PROJECT_SPACE:MANAGE',
        'QUESTION:ASK', 'QUESTION:READ', 'CONVERSATION:MANAGE',
        'AGENT_CONFIG:READ', 'SKILL:READ', 'TOOL:READ', 'MCP_SERVER:READ',
        'LOG_UPLOAD:CREATE', 'LOG_UPLOAD:READ',
        'SELF_LEARNING:READ', 'SELF_LEARNING:MANAGE'
    );
