-- Phase 4 安全加固：添加锁定时间戳字段
ALTER TABLE users ADD COLUMN lockedAt DATETIME NULL COMMENT '账户锁定时间，用于计算自动解锁';
