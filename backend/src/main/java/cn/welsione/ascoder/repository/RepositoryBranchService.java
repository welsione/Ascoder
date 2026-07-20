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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Value("${ascoder.repo-root:./data/repos}")
    private String repoRoot;

    @Transactional(readOnly = true)
    public List<RepositoryBranch> list(Long repositoryId) {
        ensureRepository(repositoryId);
        return repository.findByRepository_IdAndActiveTrueOrderByNameAscSourceKindAsc(repositoryId);
    }

    @Transactional
    public List<RepositoryBranch> refresh(Long repositoryId) {
        CodeRepository codeRepository = lockRepository(repositoryId);
        log.info("刷新仓库分支，repositoryId={}，name={}", repositoryId, codeRepository.getName());

        Path repositoryPath = Path.of(codeRepository.resolveLocalPath(repoRoot));
        if (codeRepository.getRemoteUrl() != null && !codeRepository.getRemoteUrl().isBlank()) {
            gitRepositoryService.fetch(repositoryPath);
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
