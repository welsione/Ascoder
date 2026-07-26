-- 用户表增加"是否已修改密码"标记，用于强制默认管理员首次登录后改密
ALTER TABLE users ADD COLUMN passwordChanged BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否已修改过密码；默认管理员为 FALSE，强制改密';

-- 预置默认管理员账户（用户名 admin / 密码 admin123）
-- BCrypt(strength=12) 哈希，首次登录强制修改密码
-- ON DUPLICATE KEY UPDATE：admin 已存在时重置为默认密码并标记需改密（兼容旧库已有 admin 的情况）
INSERT INTO users (username, password, nickname, enabled, accountNonLocked, loginFailCount, passwordChanged, createdAt, updatedAt)
VALUES (
    'admin',
    '$2a$12$G6lI8B3u/mnEXpjB9NcZ4..lwyDCTGj7lTr7r685fUnidiDXQJRU.',
    '默认管理员',
    TRUE,
    TRUE,
    0,
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    nickname = VALUES(nickname),
    enabled = TRUE,
    accountNonLocked = TRUE,
    loginFailCount = 0,
    passwordChanged = FALSE,
    updatedAt = CURRENT_TIMESTAMP;

-- 为默认管理员分配 ADMIN 角色
INSERT INTO userRoles (userId, roleId, createdAt)
SELECT u.id, r.id, CURRENT_TIMESTAMP
FROM users u CROSS JOIN roles r
WHERE u.username = 'admin' AND r.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM userRoles ur WHERE ur.userId = u.id AND ur.roleId = r.id
  );
