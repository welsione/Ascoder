-- V39: 将 codeRepositories.localPath 从本地开发路径迁移为容器内路径
--
-- 背景：与 V38 同类问题。旧仓库在本地开发环境创建时 localPath 存的是
--       /Users/.../data/repos/<name>，Docker 容器内访问不到。
--       容器内通过 bind mount，/app/data/repos 对应宿主机 ./data/repos。
--
-- 策略：
--   - /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder/data/repos/<name>
--       → /app/data/repos/<name>
--   - /Users/weigaolei/CodeSpace/WorkSpace/qys-skill/<name>
--       → /app/data/repos/<name>（若 <name> 在容器内 repos 目录存在）
--   - 其余路径保持不变（如已经是 /app/... 或无法判断）
--
-- 注意：Ascoder 仓库（localPath 指向项目根目录）在容器内无对应源码，
--       需用户单独处理（挂载源码或删除该仓库记录）。

-- 1. data/repos 下的仓库：替换前缀
UPDATE repositories
SET localPath = REPLACE(
        localPath,
        '/Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder/data/repos/',
        '/app/data/repos/'
    ),
    updatedAt = CURRENT_TIMESTAMP(6)
WHERE localPath LIKE '/Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder/data/repos/%';

-- 2. qys-private / qys-private-common 仓库（指向兄弟目录，但容器内已 clone 到 /app/data/repos/）
--    这些仓库在容器内 /app/data/repos/ 下有对应目录
UPDATE repositories
SET localPath = '/app/data/repos/qys-private',
    updatedAt = CURRENT_TIMESTAMP(6)
WHERE localPath = '/Users/weigaolei/CodeSpace/WorkSpace/qys-skill/qys-private';

UPDATE repositories
SET localPath = '/app/data/repos/qys-private-common',
    updatedAt = CURRENT_TIMESTAMP(6)
WHERE localPath = '/Users/weigaolei/CodeSpace/WorkSpace/qys-skill/qys-private-common';

-- 3. branchWorkspaces.worktreePath 和 codegraphIndexPath 若有本地路径，一并修复
UPDATE branchWorkspaces
SET worktreePath = REPLACE(
        worktreePath,
        '/Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder/data/worktrees/',
        '/app/data/worktrees/'
    )
WHERE worktreePath LIKE '/Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder/data/worktrees/%';

UPDATE branchWorkspaces
SET codegraphIndexPath = REPLACE(
        codegraphIndexPath,
        '/Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder/data/worktrees/',
        '/app/data/worktrees/'
    )
WHERE codegraphIndexPath LIKE '/Users/weigaolei/CodeSpace/WorkSpace/qys-skill/Ascoder/data/worktrees/%';
