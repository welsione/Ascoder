package cn.welsione.ascoder.repository;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.git.GitBranchInfo;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 仓库分支服务，负责分支发现结果的刷新、缓存和查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepositoryBranchService {

    private final RepositoryBranchJpaRepository repository;
    private final CodeRepositoryJpaRepository codeRepositoryJpaRepository;
    private final GitRepositoryService gitRepositoryService;
    private final TransactionTemplate transactionTemplate;

    @Value("${ascoder.repo-root:./data/repos}")
    private String repoRoot;

    @Transactional(readOnly = true)
    public List<RepositoryBranch> list(Long repositoryId) {
        ensureRepository(repositoryId);
        return repository.findByRepository_IdAndActiveTrueOrderByNameAscSourceKindAsc(repositoryId);
    }

    /**
     * 先 fetch 远程引用（事务外），再刷新分支（短事务）。
     *
     * <p>用于需要同时拉取远程更新并刷新分支的场景。fetch 在事务外执行，
     * 避免长耗时 git 操作持有数据库行锁导致锁等待超时。
     * DB 更新通过 {@link TransactionTemplate} 在短事务内执行，
     * 绕过 Spring AOP 自调用限制。</p>
     *
     * <p>注意：{@link #ensureRepository(Long)} 内部的 DB 查询也在无事务状态下执行，
     * 仅用于读取仓库元数据（路径、远程 URL），不涉及锁竞争，无需事务保护。</p>
     *
     * @param repositoryId 仓库 ID
     * @param onLine fetch 阶段的行输出回调，可为 null
     * @return 活跃分支列表
     */
    public List<RepositoryBranch> fetchAndRefresh(Long repositoryId, Consumer<String> onLine) {
        CodeRepository repo = ensureRepository(repositoryId);
        Path repositoryPath = Path.of(repo.resolveLocalPath(repoRoot));
        if (repo.getRemoteUrl() != null && !repo.getRemoteUrl().isBlank()) {
            gitRepositoryService.fetch(repositoryPath, onLine);
        }
        return doRefreshInTransaction(repositoryId);
    }

    /**
     * 刷新仓库分支：从 git 发现分支并更新 DB。
     *
     * <p>注意：此方法不再执行 git fetch，调用方需在调用前自行完成 fetch/pull。
     * 事务由 {@link #doRefreshInTransaction(Long)} 通过 {@link TransactionTemplate} 控制，
     * listRemoteHeads + listBranches 均在该短事务内执行，这两者为快速操作（秒级），
     * 不会导致锁等待超时。</p>
     */
    public List<RepositoryBranch> refresh(Long repositoryId) {
        return doRefreshInTransaction(repositoryId);
    }

    /**
     * 在短事务内执行分支发现与 DB 更新。
     *
     * <p>抽取自 {@link #refresh(Long)} 和 {@link #fetchAndRefresh(Long, Consumer)}，
     * 消除重复代码。使用 {@link TransactionTemplate} 确保事务边界，
     * 绕过 Spring AOP 自调用限制（{@code fetchAndRefresh} 调用 {@code this.refresh} 时
     * {@code @Transactional} 不生效）。</p>
     */
    private List<RepositoryBranch> doRefreshInTransaction(Long repositoryId) {
        return transactionTemplate.execute(status -> doRefresh(repositoryId));
    }

    private List<RepositoryBranch> doRefresh(Long repositoryId) {
        CodeRepository codeRepository = lockRepository(repositoryId);
        log.info("刷新仓库分支，repositoryId={}，name={}", repositoryId, codeRepository.getName());

        Path repositoryPath = Path.of(codeRepository.resolveLocalPath(repoRoot));
        if (codeRepository.getRemoteUrl() != null && !codeRepository.getRemoteUrl().isBlank()) {
            codeRepository.pulled(new Date());
            codeRepositoryJpaRepository.save(codeRepository);
        }

        List<GitBranchInfo> discovered = new ArrayList<>();
        if (codeRepository.getRemoteUrl() != null && !codeRepository.getRemoteUrl().isBlank()) {
            discovered.addAll(gitRepositoryService.listRemoteHeads(repositoryPath));
        }
        discovered.addAll(gitRepositoryService.listBranches(repositoryPath));
        if (discovered.isEmpty()) {
            throw new ValidationException("仓库未发现可用分支");
        }

        Date seenAt = new Date();
        Map<String, GitBranchInfo> discoveredByName = discovered.stream()
                .filter(branch -> branch.getBranchName() != null && !branch.getBranchName().isBlank())
                .filter(branch -> branch.getRefName() != null && !branch.getRefName().isBlank())
                .collect(Collectors.toMap(GitBranchInfo::getBranchName, Function.identity(), this::preferBranchRef,
                        LinkedHashMap::new));
        // 按 refName 小写去重，避免大小写不敏感文件系统上的唯一约束冲突
        Map<String, GitBranchInfo> dedupedByRefName = new LinkedHashMap<>();
        for (GitBranchInfo info : discoveredByName.values()) {
            String key = info.getRefName().toLowerCase();
            if (!dedupedByRefName.containsKey(key)) {
                dedupedByRefName.put(key, info);
            }
        }
        Map<String, RepositoryBranch> existingByRef = repository.findByRepository_IdOrderByNameAscSourceKindAsc(repositoryId)
                .stream()
                .collect(Collectors.toMap(
                        b -> b.getRefName().toLowerCase(),
                        Function.identity(),
                        this::preferExistingBranch));

        List<RepositoryBranch> branchesToSave = new ArrayList<>();
        for (GitBranchInfo info : dedupedByRefName.values()) {
            RepositoryBranch branch = existingByRef.getOrDefault(info.getRefName().toLowerCase(), new RepositoryBranch());
            branch.setRepository(codeRepository);
            branch.updateFrom(
                    info.getBranchName(),
                    info.getRefName(),
                    info.getCommitSha(),
                    info.getRemoteName(),
                    info.getSourceKind(),
                    seenAt
            );
            branchesToSave.add(branch);
        }
        existingByRef.forEach((refName, branch) -> {
            if (branch.isActive() && missingFromPreferredRefs(dedupedByRefName, branch, refName)) {
                branch.deactivate();
                branchesToSave.add(branch);
            }
        });
        repository.saveAll(branchesToSave);
        return repository.findByRepository_IdAndActiveTrueOrderByNameAscSourceKindAsc(repositoryId);
    }

    @Transactional(readOnly = true)
    public RepositoryBranch getActiveEntity(Long branchId, Long repositoryId) {
        RepositoryBranch branch = repository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("仓库分支", branchId));
        if (!branch.getRepositoryId().equals(repositoryId)) {
            throw new ValidationException("分支不属于当前仓库");
        }
        if (!branch.isActive()) {
            throw new ValidationException("分支已不在最新发现清单中，请刷新仓库分支");
        }
        return branch;
    }

    private CodeRepository ensureRepository(Long repositoryId) {
        return codeRepositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("仓库", repositoryId));
    }

    private CodeRepository lockRepository(Long repositoryId) {
        return codeRepositoryJpaRepository.findByIdForUpdate(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("仓库", repositoryId));
    }

    private GitBranchInfo preferBranchRef(GitBranchInfo left, GitBranchInfo right) {
        return sourcePriority(left.getSourceKind()) <= sourcePriority(right.getSourceKind()) ? left : right;
    }

    private int sourcePriority(RepositoryBranchSourceKind sourceKind) {
        if (sourceKind == RepositoryBranchSourceKind.REMOTE_HEAD) {
            return 0;
        }
        if (sourceKind == RepositoryBranchSourceKind.LOCAL_HEAD) {
            return 1;
        }
        return 2;
    }

    private boolean missingFromPreferredRefs(Map<String, GitBranchInfo> dedupedByRefName,
                                             RepositoryBranch branch,
                                             String refName) {
        GitBranchInfo preferred = dedupedByRefName.get(branch.getRefName().toLowerCase());
        return preferred == null;
    }

    private RepositoryBranch preferExistingBranch(RepositoryBranch a, RepositoryBranch b) {
        return a;
    }
}
