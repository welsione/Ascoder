package cn.welsione.ascoder.repository;

import cn.welsione.ascoder.common.FileUtil;
import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.common.task.TaskEngine;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskSubmitRequest;
import cn.welsione.ascoder.codegraph.task.CodeGraphIndexContext;
import cn.welsione.ascoder.repository.git.GitCredentialStore;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import cn.welsione.ascoder.repository.task.BranchRefreshContext;
import cn.welsione.ascoder.repository.task.GitCloneContext;
import cn.welsione.ascoder.repository.task.GitFetchContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

/**
 * 仓库服务，处理代码仓库的 CRUD 和 CodeGraph 索引操作。
 */
@Slf4j
@Service
public class RepositoryService {

    private final CodeRepositoryJpaRepository repository;
    private final RepositoryPathValidator pathValidator;
    private final GitRepositoryService gitRepositoryService;
    private final GitCredentialStore gitCredentialStore;
    private final TaskEngine taskEngine;
    private final RepositoryDeletionGuard deletionGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final RepositoryBranchJpaRepository branchRepository;

    private final Path repoRoot;
    private final Path worktreeRoot;

    public RepositoryService(
            CodeRepositoryJpaRepository repository,
            RepositoryPathValidator pathValidator,
            GitRepositoryService gitRepositoryService,
            GitCredentialStore gitCredentialStore,
            TaskEngine taskEngine,
            RepositoryDeletionGuard deletionGuard,
            ApplicationEventPublisher eventPublisher,
            RepositoryBranchJpaRepository branchRepository,
            @Value("${ascoder.repo-root}") String repoRoot,
            @Value("${ascoder.worktree-root:./data/worktrees}") String worktreeRoot
    ) {
        this.repository = repository;
        this.pathValidator = pathValidator;
        this.gitRepositoryService = gitRepositoryService;
        this.gitCredentialStore = gitCredentialStore;
        this.taskEngine = taskEngine;
        this.deletionGuard = deletionGuard;
        this.eventPublisher = eventPublisher;
        this.branchRepository = branchRepository;
        this.repoRoot = pathValidator.normalizeRepoRoot(repoRoot);
        this.worktreeRoot = Path.of(worktreeRoot).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<CodeRepository> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public CodeRepository get(Long id) {
        return getEntity(id);
    }

    @Transactional
    public CodeRepository create(CreateRepositoryRequest request) {
        log.info("创建仓库，name={}，localPath={}，remoteUrl={}", request.getName(), request.getLocalPath(), request.getRemoteUrl());

        if (repository.existsByName(request.getName().trim())) {
            throw new DuplicateException("仓库名称已存在");
        }

        Path normalizedPath = resolveRepositoryPath(request);
        boolean remoteRepository = hasText(request.getRemoteUrl());

        CodeRepository entity = new CodeRepository();
        entity.setName(request.getName().trim());
        // 存储相对路径（basename），运行时由 resolveLocalPath(repoRoot) 拼接绝对路径
        entity.setLocalPath(normalizedPath.getFileName().toString());
        entity.setRemoteUrl(trimToNull(request.getRemoteUrl()));
        entity.setDefaultBranch(trimToNull(request.getDefaultBranch()));
        entity.setAuthUsername(trimToNull(request.getAuthUsername()));
        entity.setAuthPassword(trimToNull(request.getAuthPassword()));

        try {
            if (remoteRepository) {
                upsertCredentials(request.getRemoteUrl(), request.getAuthUsername(), request.getAuthPassword());
                entity.cloning();
            } else {
                entity.setStatus(RepositoryStatus.CREATED);
            }
            CodeRepository saved = repository.saveAndFlush(entity);

            if (remoteRepository) {
                GitCloneContext context = new GitCloneContext(
                        request.getRemoteUrl().trim(),
                        normalizedPath.toString(),
                        trimToNull(request.getDefaultBranch()),
                        saved.getId(),
                        trimToNull(request.getAuthUsername()),
                        trimToNull(request.getAuthPassword())
                );
                TaskSubmitRequest<GitCloneContext> taskRequest = new TaskSubmitRequest<>();
                taskRequest.setKind(TaskKind.GIT_CLONE);
                taskRequest.setContext(context);
                taskRequest.setBusinessId(saved.getId());
                taskEngine.submit(taskRequest);
                log.info("已提交 Git clone 异步任务，repositoryId={}", saved.getId());
            }

            return saved;
        } catch (IllegalStateException ex) {
            throw new ValidationException(ex.getMessage(), ex);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateException("仓库名称已存在", ex);
        }
    }

    @Transactional
    public CodeRepository index(Long id) {
        log.info("开始索引仓库，id={}", id);
        CodeRepository entity = getEntity(id);
        if (entity.getStatus() == RepositoryStatus.INDEXING) {
            throw new InvalidStateException("仓库正在索引中");
        }

        entity.indexing();
        repository.saveAndFlush(entity);

        CodeGraphIndexContext context = new CodeGraphIndexContext(
                entity.resolveLocalPath(repoRoot.toString()),
                null, false, null, id
        );
        TaskSubmitRequest<CodeGraphIndexContext> request = new TaskSubmitRequest<>();
        request.setKind(TaskKind.CODEGRAPH_INDEX);
        request.setContext(context);
        request.setBusinessId(id);
        taskEngine.submit(request);
        log.info("已提交 CodeGraph 索引异步任务，repositoryId={}", id);

        return entity;
    }

    @Transactional(readOnly = true)
    public CodeRepository indexStatus(Long id) {
        return get(id);
    }

    @Transactional
    public CodeRepository fetch(Long id) {
        CodeRepository entity = getEntity(id);
        upsertCredentials(entity);
        entity.syncing();
        repository.saveAndFlush(entity);

        GitFetchContext context = new GitFetchContext(
                entity.resolveLocalPath(repoRoot.toString()),
                id, GitSyncOperation.FETCH,
                entity.getAuthUsername(), entity.getAuthPassword(), entity.getRemoteUrl(),
                null
        );
        TaskSubmitRequest<GitFetchContext> fetchRequest = new TaskSubmitRequest<>();
        fetchRequest.setKind(TaskKind.GIT_FETCH);
        fetchRequest.setContext(context);
        fetchRequest.setBusinessId(id);
        taskEngine.submit(fetchRequest);
        log.info("已提交 Git fetch 异步任务，repositoryId={}", id);

        return entity;
    }

    @Transactional
    public CodeRepository pull(Long id) {
        CodeRepository entity = getEntity(id);
        upsertCredentials(entity);
        entity.syncing();
        repository.saveAndFlush(entity);

        GitFetchContext context = new GitFetchContext(
                entity.resolveLocalPath(repoRoot.toString()),
                id, GitSyncOperation.PULL,
                entity.getAuthUsername(), entity.getAuthPassword(), entity.getRemoteUrl(),
                null
        );
        TaskSubmitRequest<GitFetchContext> pullRequest = new TaskSubmitRequest<>();
        pullRequest.setKind(TaskKind.GIT_FETCH);
        pullRequest.setContext(context);
        pullRequest.setBusinessId(id);
        taskEngine.submit(pullRequest);
        log.info("已提交 Git pull 异步任务，repositoryId={}", id);

        return entity;
    }

    @Transactional(readOnly = true)
    public CodeRepository getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("仓库", id));
    }

    /**
     * 提交分支刷新异步任务。
     *
     * <p>提交 BRANCH_REFRESH 任务后立即返回，不等待 fetch + 分支发现完成。
     * 凭据在提交前已写入 credential store，任务执行时直接读取。
     * 用户可通过 GET /{id}/branches 查询最新分支列表。</p>
     */
    @Transactional
    public void refreshBranches(Long id) {
        CodeRepository entity = getEntity(id);
        upsertCredentials(entity);

        BranchRefreshContext context = new BranchRefreshContext(id);
        TaskSubmitRequest<BranchRefreshContext> request = new TaskSubmitRequest<>();
        request.setKind(TaskKind.BRANCH_REFRESH);
        request.setContext(context);
        request.setBusinessId(id);
        taskEngine.submit(request);
        log.info("已提交分支刷新异步任务，repositoryId={}", id);
    }

    /**
     * 更新仓库的 Git 认证凭据，并同步写入 credential store。
     */
    @Transactional
    public CodeRepository updateCredentials(Long id, UpdateRepositoryCredentialsRequest request) {
        CodeRepository entity = getEntity(id);
        entity.setAuthUsername(trimToNull(request.getAuthUsername()));
        entity.setAuthPassword(trimToNull(request.getAuthPassword()));
        upsertCredentials(entity);
        return repository.save(entity);
    }

    /**
     * 重命名仓库。仅修改名称，不影响本地路径与远程地址。
     *
     * @throws DuplicateException 名称已被其他仓库占用
     */
    @Transactional
    public CodeRepository rename(Long id, RenameRepositoryRequest request) {
        String newName = request.getName().trim();
        log.info("重命名仓库，id={}，newName={}", id, newName);

        CodeRepository entity = getEntity(id);
        if (entity.getName().equals(newName)) {
            return entity;
        }
        if (repository.existsByName(newName)) {
            throw new DuplicateException("仓库名称已存在");
        }
        entity.setName(newName);
        try {
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateException("仓库名称已存在", ex);
        }
    }

    /**
     * 删除仓库及其本地克隆目录与 worktree。
     *
     * <p>删除前进行强关联引用检查（项目仓库成员、项目空间成员、分支工作区、分支引用），
     * 存在引用时拒绝删除。questions / conversations / 自学习等 nullable 引用由
     * {@link RepositoryDeletedEvent} 监听器在删除时置 null，保留历史数据。</p>
     *
     * <p>事务边界：引用检查与 DB 删除在事务内，磁盘清理在事务内先行执行
     * （参考 {@code ProjectSpaceService.delete}），失败则事务回滚。</p>
     *
     * @throws InvalidStateException 仓库正在处理中或被强关联引用
     */
    @Transactional
    public void delete(Long id) {
        CodeRepository entity = getEntity(id);
        log.info("删除仓库，id={}，name={}", id, entity.getName());

        if (entity.getStatus() == RepositoryStatus.CLONING
                || entity.getStatus() == RepositoryStatus.SYNCING
                || entity.getStatus() == RepositoryStatus.INDEXING) {
            throw new InvalidStateException("仓库正在处理中，无法删除");
        }

        String blockingReason = deletionGuard.checkBlocking(id);
        if (blockingReason != null) {
            throw new InvalidStateException(blockingReason);
        }

        // 先清理磁盘：本地克隆目录与 worktree 目录，均校验在托管根目录下
        deleteLocalFiles(entity);

        // 发布事件，由 question / selflearning 聚合监听器解除 nullable 引用（BEFORE_COMMIT）
        eventPublisher.publishEvent(new RepositoryDeletedEvent(id));

        // 删除仓库分支引用记录（同聚合强关联，NOT NULL）
        branchRepository.deleteAll(branchRepository.findByRepository_IdOrderByNameAscSourceKindAsc(id));
        repository.delete(entity);
        log.info("仓库已删除，id={}，name={}", id, entity.getName());
    }

    /**
     * 删除仓库的本地克隆目录与 worktree 目录。
     *
     * <p>两处路径均通过 {@link FileUtil#ensureUnderRoot} 校验在托管根目录下，
     * 避免误删根目录之外的文件。worktree 目录以仓库名为子目录。</p>
     */
    private void deleteLocalFiles(CodeRepository entity) {
        Path clonePath = Path.of(entity.resolveLocalPath(repoRoot.toString()));
        FileUtil.ensureUnderRoot(clonePath, repoRoot, "仓库克隆目录");
        FileUtil.deleteDirectoryIfExists(clonePath);

        Path worktreeRepoPath = worktreeRoot.resolve(FileUtil.safePathPart(entity.getName()));
        FileUtil.ensureUnderRoot(worktreeRepoPath, worktreeRoot, "仓库 worktree 目录");
        FileUtil.deleteDirectoryIfExists(worktreeRepoPath);
    }

    private Path resolveRepositoryPath(CreateRepositoryRequest request) {
        try {
            if (hasText(request.getRemoteUrl())) {
                String path = hasText(request.getLocalPath()) ? request.getLocalPath().trim() : FileUtil.safePathPart(request.getName().trim());
                return pathValidator.resolveCreatableUnderRoot(repoRoot, path);
            }
            if (!hasText(request.getLocalPath())) {
                throw new InvalidRepositoryPathException("本地仓库必须填写 localPath，远程仓库必须填写 remoteUrl");
            }
            return pathValidator.validateUnderRoot(repoRoot, request.getLocalPath().trim());
        } catch (InvalidRepositoryPathException ex) {
            throw new ValidationException(ex.getMessage(), ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    /**
     * 将仓库级凭据写入 credential store，确保后续 Git 操作能通过认证。
     *
     * <p>若实体的 remoteUrl 不是 HTTPS URL（可能是本地路径或相对路径），
     * 则从仓库的 .git/config 中读取实际的远程 origin URL。</p>
     */
    private void upsertCredentials(CodeRepository entity) {
        if (!hasText(entity.getAuthUsername()) || !hasText(entity.getAuthPassword())) {
            return;
        }
        String remoteUrl = entity.getRemoteUrl();
        if (!isHttpsUrl(remoteUrl)) {
            remoteUrl = resolveOriginUrl(entity);
        }
        if (hasText(remoteUrl)) {
            gitCredentialStore.upsert(remoteUrl, entity.getAuthUsername(), entity.getAuthPassword());
        }
    }

    private void upsertCredentials(String remoteUrl, String username, String password) {
        if (hasText(remoteUrl) && hasText(username) && hasText(password)) {
            gitCredentialStore.upsert(remoteUrl, username, password);
        }
    }

    private boolean isHttpsUrl(String url) {
        return url != null && url.startsWith("https://");
    }

    /**
     * 从仓库的 .git/config 中读取 origin 的远程 URL。
     */
    private String resolveOriginUrl(CodeRepository entity) {
        try {
            Path repoPath = Path.of(entity.resolveLocalPath(repoRoot.toString()));
            return gitRepositoryService.getRemoteUrl(repoPath);
        } catch (RuntimeException ex) {
            log.warn("读取仓库远程 URL 失败，repositoryId={}：{}", entity.getId(), ex.getMessage());
            return null;
        }
    }
}
