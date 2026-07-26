-- 用户表增加"是否已修改密码"标记，用于强制默认管理员首次登录后改密
ALTER TABLE users ADD COLUMN passwordChanged BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否已修改过密码；默认管理员为 FALSE，强制改密';

-- 预置默认管理员账户（用户名 admin / 密码 admin123）
-- BCrypt(strength=12) 哈希，首次登录强制修改密码
-- 使用 SELECT WHERE NOT EXISTS 守卫，避免测试库残留导致重复插入失败
INSERT INTO users (username, password, nickname, enabled, accountNonLocked, loginFailCount, passwordChanged, createdAt, updatedAt)
SELECT
    'admin',
    '$2a$12$G6lI8B3u/mnEXpjB9NcZ4..lwyDCTGj7lTr7r685fUnidiDXQJRU.',
    '默认管理员',
    TRUE,
    TRUE,
    0,
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- 为默认管理员分配 ADMIN 角色
INSERT INTO userRoles (userId, roleId, createdAt)
SELECT u.id, r.id, CURRENT_TIMESTAMP
FROM users u CROSS JOIN roles r
WHERE u.username = 'admin' AND r.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM userRoles ur WHERE ur.userId = u.id AND ur.roleId = r.id
  );
