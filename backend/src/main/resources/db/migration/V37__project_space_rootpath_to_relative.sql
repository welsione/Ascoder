-- V37: 将 projectSpaces.rootPath 从绝对路径迁移为相对目录名
--
-- 背景：rootPath 当前存储绝对路径（如 /Users/.../data/project-spaces/qys-private-hotfix_5.5.x），
--       在创建项目空间时基于 projectSpaceRoot 配置计算。当本地开发环境创建的数据
--       在 Docker 容器内运行时，绝对路径不存在（容器内路径为 /app/data/project-spaces/...），
--       导致 prepare / index / refresh / Agent session 创建等操作全部失败。
--
-- 迁移策略：把 rootPath 改为只存储空间目录名（如 qys-private-hotfix_5.5.x）。
--           运行时基于配置的 projectSpaceRoot + 目录名动态解析为完整路径。
--
-- 回滚策略：本迁移只修改 rootPath 为目录名是不可逆的（绝对路径 -> 相对路径）。
--         如需回滚，需要先读取当前 rootPath 的目录名，再结合 projectSpaceRoot 配置
--         重新拼接为绝对路径。

UPDATE projectSpaces
SET rootPath = SUBSTRING_INDEX(rootPath, '/', -1),
    updatedAt = CURRENT_TIMESTAMP(6)
WHERE rootPath LIKE '%/%';

-- 也需更新 projectSpaceMembers.linkPath（去掉前缀，只保留 spaceName/alias 两段）
UPDATE projectSpaceMembers m
JOIN projectSpaces s ON s.id = m.projectSpaceId
SET m.linkPath = CONCAT(
        SUBSTRING_INDEX(SUBSTRING_INDEX(m.linkPath, '/', -2), '/', 1),
        '/',
        SUBSTRING_INDEX(m.linkPath, '/', -1)
    )
WHERE m.linkPath LIKE '/%';
