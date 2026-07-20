package cn.welsione.ascoder.repository;

import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.FileUtil;
import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.git.GitCredentialStore;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;

/**
 * 仓库服务，处理代码仓库的 CRUD 和 CodeGraph 索引操作。
 */
@Slf4j
@Service
public class RepositoryService {

    private final CodeRepositoryJpaRepository repository;
    private final RepositoryPathValidator pathValidator;
    private final CodeGraphClient codeGraphClient;
    private final GitRepositoryService gitRepositoryService;
    private final GitCredentialStore gitCredentialStore;
    private final RepositoryBranchService repositoryBranchService;

    private final Path repoRoot;

    public RepositoryService(
            CodeRepositoryJpaRepository repository,
            RepositoryPathValidator pathValidator,
            CodeGraphClient codeGraphClient,
            GitRepositoryService gitRepositoryService,
            GitCredentialStore gitCredentialStore,
            RepositoryBranchService repositoryBranchService,
            @Value("${ascoder.repo-root}") String repoRoot
    ) {
        this.repository = repository;
        this.pathValidator = pathValidator;
        this.codeGraphClient = codeGraphClient;
        this.gitRepositoryService = gitRepositoryService;
        this.gitCredentialStore = gitCredentialStore;
        this.repositoryBranchService = repositoryBranchService;
        this.repoRoot = pathValidator.normalizeRepoRoot(repoRoot);
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
        entity.setStatus(RepositoryStatus.CREATED);

        try {
            if (remoteRepository) {
                upsertCredentials(request.getRemoteUrl(), request.getAuthUsername(), request.getAuthPassword());
                gitRepositoryService.cloneRepository(request.getRemoteUrl().trim(), normalizedPath, request.getDefaultBranch());
                entity.setDefaultBranch(defaultBranch(entity.getDefaultBranch(), normalizedPath));
                entity.pulled(new Date());
            }
            CodeRepository saved = repository.saveAndFlush(entity);
            refreshBranchesQuietly(saved.getId());
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

        CodeGraphToolResult result = codeGraphClient.index(Path.of(entity.resolveLocalPath(repoRoot.toString())));
        if (result.isSuccess()) {
            entity.ready(new Date());
            log.info("仓库索引完成，id={}", id);
        } else {
            entity.fail(result.getOutput());
            log.warn("仓库索引失败，id={}，错误={}", id, result.getOutput());
        }

        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public CodeRepository indexStatus(Long id) {
        return get(id);
    }

    @Transactional
    public CodeRepository fetch(Long id) {
        CodeRepository entity = getEntity(id);
        try {
            upsertCredentials(entity);
            gitRepositoryService.fetch(Path.of(entity.resolveLocalPath(repoRoot.toString())));
            entity.pulled(new Date());
            repositoryBranchService.refresh(id);
        } catch (RuntimeException ex) {
            entity.pullFailed(ex.getMessage());
        }
        return repository.save(entity);
    }

    @Transactional
    public CodeRepository pull(Long id) {
        CodeRepository entity = getEntity(id);
        try {
            upsertCredentials(entity);
            gitRepositoryService.pull(Path.of(entity.resolveLocalPath(repoRoot.toString())));
            entity.setDefaultBranch(defaultBranch(entity.getDefaultBranch(), Path.of(entity.resolveLocalPath(repoRoot.toString()))));
            entity.pulled(new Date());
            repositoryBranchService.refresh(id);
        } catch (RuntimeException ex) {
            entity.pullFailed(ex.getMessage());
        }
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public CodeRepository getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("仓库", id));
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

    private String defaultBranch(String configuredBranch, Path repositoryPath) {
        if (hasText(configuredBranch)) {
            return configuredBranch.trim();
        }
        try {
            return gitRepositoryService.currentBranch(repositoryPath);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private void refreshBranchesQuietly(Long repositoryId) {
        try {
            repositoryBranchService.refresh(repositoryId);
        } catch (RuntimeException ex) {
            log.warn("仓库分支发现失败，repositoryId={}，错误={}", repositoryId, ex.getMessage());
        }
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
