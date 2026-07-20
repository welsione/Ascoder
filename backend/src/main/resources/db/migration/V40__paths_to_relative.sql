-- V40: 将所有路径字段从绝对路径改为相对路径，实现数据可移植
--
-- 背景：V37/V38/V39 是环境特定的数据修补，只解决当前 Docker 环境问题。
--       同一份 DB 在其他环境（其他机器、不同部署路径）无法复用。
--       改为相对路径后，运行时基于配置的 root 拼接绝对路径。
--
-- 通用策略（不依赖特定路径前缀）：
--   - 已是相对路径（不含 / 或盘符前缀）：保持不变
--   - 是绝对路径：提取 basename（localPath/rootPath）或保留最后两段
--
-- 注：本迁移幂等（重复执行结果一致），对其他环境也安全（不匹配时 0 行变更）。

-- 1. repositories.localPath: 提取最后一段目录名
UPDATE repositories
SET localPath = SUBSTRING_INDEX(localPath, '/', -1),
    updatedAt = CURRENT_TIMESTAMP(6)
WHERE localPath LIKE '/%' AND LOCATE('/', localPath) > 0;

-- 兼容 Windows 路径（盘符前缀），但不常见，留给后续处理
-- 此处只处理 POSIX 风格

-- 2. projectSpaces.rootPath: 提取最后一段目录名
UPDATE projectSpaces
SET rootPath = SUBSTRING_INDEX(rootPath, '/', -1),
    updatedAt = CURRENT_TIMESTAMP(6)
WHERE rootPath LIKE '/%' AND LOCATE('/', rootPath) > 0;

-- 3. projectSpaceMembers.linkPath: 保留 spaceName/alias 两段
--    路径结构如 /app/data/project-spaces/qys-private-hotfix_5.5.x/jprompt
--    截取后两段：qys-private-hotfix_5.5.x/jprompt
UPDATE projectSpaceMembers
SET linkPath = CONCAT(
        SUBSTRING_INDEX(SUBSTRING_INDEX(linkPath, '/', -2), '/', 1),
        '/',
        SUBSTRING_INDEX(linkPath, '/', -1)
    )
WHERE linkPath LIKE '/%' AND linkPath REGEXP '/[^/]+/[^/]+';

-- 4. branchWorkspaces.worktreePath: 保留 repoName/branchName 两段
UPDATE branchWorkspaces
SET worktreePath = CONCAT(
        SUBSTRING_INDEX(SUBSTRING_INDEX(worktreePath, '/', -2), '/', 1),
        '/',
        SUBSTRING_INDEX(worktreePath, '/', -1)
    ),
    updatedAt = CURRENT_TIMESTAMP(6)
WHERE worktreePath IS NOT NULL
  AND worktreePath != ''
  AND worktreePath LIKE '/%'
  AND worktreePath REGEXP '/[^/]+/[^/]+';

-- 5. branchWorkspaces.codegraphIndexPath: worktreePath + "/.codegraph"
--    路径结构如 /app/data/worktrees/jprompt/master/.codegraph
--    直接重写为 "<repoName>/<branchName>/.codegraph"
UPDATE branchWorkspaces
SET codegraphIndexPath = CONCAT(
        SUBSTRING_INDEX(SUBSTRING_INDEX(worktreePath, '/', -2), '/', 1),
        '/',
        SUBSTRING_INDEX(worktreePath, '/', -1),
        '/.codegraph'
    ),
    updatedAt = CURRENT_TIMESTAMP(6)
WHERE worktreePath IS NOT NULL
  AND worktreePath != ''
  AND worktreePath LIKE '/%'
  AND worktreePath REGEXP '/[^/]+/[^/]+';