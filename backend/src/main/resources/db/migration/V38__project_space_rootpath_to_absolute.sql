-- V38: 将 projectSpaces.rootPath 和 projectSpaceMembers.linkPath 从相对目录名
--       还原为容器内绝对路径。
--
-- 背景：V37 把 rootPath 从绝对路径迁移为相对目录名，但这要求代码侧同步改造
--       （运行时拼接 projectSpaceRoot + 目录名）。由于代码未改造，且 Docker
--       容器内 projectSpaceRoot 固定为 /app/data/project-spaces，直接在数据
--       层把 rootPath 改为容器内绝对路径即可，代码无需改动。
--
-- 策略：
--   - rootPath: 若不含路径分隔符（相对目录名），拼接 /app/data/project-spaces/ 前缀
--   - linkPath: 若是相对路径（spaceName/alias 形式），拼接 /app/data/project-spaces/ 前缀
--
-- 注意：本迁移假设部署环境为 Docker 容器（projectSpaceRoot=/app/data/project-spaces）。
--       本地开发环境若需使用这些项目空间，需手动改回本地路径。

UPDATE projectSpaces
SET rootPath = CONCAT('/app/data/project-spaces/', rootPath),
    updatedAt = CURRENT_TIMESTAMP(6)
WHERE rootPath NOT LIKE '/%';

UPDATE projectSpaceMembers
SET linkPath = CONCAT('/app/data/project-spaces/', linkPath)
WHERE linkPath IS NOT NULL
  AND linkPath != ''
  AND linkPath NOT LIKE '/%';
