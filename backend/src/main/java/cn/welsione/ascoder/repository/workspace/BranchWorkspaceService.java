package cn.welsione.ascoder.repository.workspace;

import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.common.FileUtil;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.RepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理仓库分支对应的独立 worktree，并维护该分支可复现分析所需的索引状态。
 */
@Slf4j
@Service
public class BranchWorkspaceService {

    private final BranchWorkspaceJpaRepository repository;
    private final RepositoryService repositoryService;
    private final GitRepositoryService gitRepositoryService;
    private final CodeGraphClient codeGraphClient;
    private final TransactionTemplate transactionTemplate;
    private final Path worktreeRoot;

    private final String repoRoot;

    public BranchWorkspaceService(
            BranchWorkspaceJpaRepository repository,
            RepositoryService repositoryService,
            GitRepositoryService gitRepositoryService,
            CodeGraphClient codeGraphClient,
            TransactionTemplate transactionTemplate,
            @Value("${ascoder.worktree-root:./data/worktrees}") String worktreeRoot,
            @Value("${ascoder.repo-root:./data/repos}") String repoRoot
    ) {
        this.repository = repository;
        this.repositoryService = repositoryService;
        this.gitRepositoryService = gitRepositoryService;
        this.codeGraphClient = codeGraphClient;
        this.transactionTemplate = transactionTemplate;
        this.worktreeRoot = Path.of(worktreeRoot).toAbsolutePath().normalize();
        this.repoRoot = repoRoot;
    }

    /**
     * @deprecated 前端未接入分支 workspace 管理 UI，无调用方，预留 API 暂不维护。
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<BranchWorkspace> list(Long repositoryId) {
        return repositoryId == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByRepository_IdOrderByBranchNameAsc(repositoryId);
    }

    /**
     * @deprecated 前端未接入分支 workspace 管理 UI，无调用方，预留 API 暂不维护。
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<GitBranchResponse> listBranches(Long repositoryId) {
        CodeRepository codeRepo = repositoryService.getEntity(repositoryId);
        Map<String, BranchWorkspace> workspaceByBranch = repository.findByRepository_IdOrderByBranchNameAsc(repositoryId)
                .stream()
                .collect(Collectors.toMap(BranchWorkspace::getBranchName, Function.identity()));

        return gitRepositoryService.listBranches(Path.of(codeRepo.resolveLocalPath(repoRoot))).stream()
                .map(branch -> {
                    BranchWorkspace workspace = workspaceByBranch.get(branch.getBranchName());
                    return new GitBranchResponse(
                            branch.getBranchName(),
                            branch.getCommitSha(),
                            workspace == null ? null : workspace.getStatus(),
                            workspace == null ? null : workspace.getId()
                    );
                })
                .toList();
    }

    public BranchWorkspace prepare(Long repositoryId, CreateBranchWorkspaceRequest request) {
        return prepare(repositoryId, request, null);
    }

    /**
     * 为分支创建/复用 worktree 并标记 PREPARING，再于事务外执行 git worktree 创建，
     * 最后短事务回写 READY/FAILED 状态。
     *
     * <p>事务边界：git worktree 创建（可能耗时）在事务外执行，避免长时间持有数据库连接；
     * DB 状态流转通过 {@link TransactionTemplate} 短事务完成。</p>
     */
    public BranchWorkspace prepare(Long repositoryId, CreateBranchWorkspaceRequest request, String selectedCommitSha) {
        CodeRepository codeRepo = repositoryService.getEntity(repositoryId);
        String branchName = request.getBranchName().trim();
        BranchWorkspace workspace = repository.findByRepository_IdAndBranchName(repositoryId, branchName)
                .orElseGet(() -> createWorkspace(codeRepo, branchName, selectedCommitSha));
        workspace.setRepository(codeRepo);
        workspace.preparing();
        transactionTemplate.executeWithoutResult(status -> repository.saveAndFlush(workspace));

        try {
            String commitSha = selectedCommitSha == null || selectedCommitSha.isBlank()
                    ? gitRepositoryService.commitSha(Path.of(codeRepo.resolveLocalPath(repoRoot)), branchName)
                    : selectedCommitSha.trim();
            String commitMessage = gitRepositoryService.commitMessage(Path.of(codeRepo.resolveLocalPath(repoRoot)), commitSha);
            gitRepositoryService.upsertDetachedWorktree(
                    Path.of(codeRepo.resolveLocalPath(repoRoot)),
                    branchName,
                    commitSha,
                    Path.of(workspace.resolveWorktreePath(worktreeRoot.toString()))
            );
            return updateInTransaction(workspace.getId(), managed -> managed.ready(commitSha, commitMessage));
        } catch (RuntimeException ex) {
            updateInTransaction(workspace.getId(), managed -> managed.fail(ex.getMessage()));
            throw new ValidationException(ex.getMessage(), ex);
        }
    }

    /**
     * @deprecated 前端未接入分支 workspace 管理 UI，无调用方。且在事务内同步执行 CodeGraph 全量索引，
     * 存在长时间持有数据库连接的隐患，因未触发故暂不修复。TODO 后续接入前端时需重构为异步任务 + 短事务模式。
     */
    @Deprecated
    @Transactional
    public BranchWorkspace index(Long id) {
        BranchWorkspace workspace = getEntity(id);
        if (workspace.getStatus() == BranchWorkspaceStatus.INDEXING) {
            throw new InvalidStateException("分支 workspace 正在索引中");
        }

        workspace.indexing();
        repository.saveAndFlush(workspace);
        try {
            String commitSha = gitRepositoryService.commitSha(
                    Path.of(workspace.getRepository().resolveLocalPath(repoRoot)),
                    workspace.getBranchName()
            );
            gitRepositoryService.upsertDetachedWorktree(
                    Path.of(workspace.getRepository().resolveLocalPath(repoRoot)),
                    workspace.getBranchName(),
                    commitSha,
                    Path.of(workspace.resolveWorktreePath(worktreeRoot.toString()))
            );
            Path worktreePath = Path.of(workspace.resolveWorktreePath(worktreeRoot.toString())).toAbsolutePath().normalize();
            Path indexPath = effectiveCodegraphIndexPath(workspace);
            CodeGraphToolResult result = codeGraphClient.index(worktreePath, indexPath, null);
            if (result.isSuccess()) {
                String commitMessage = gitRepositoryService.commitMessage(
                        Path.of(workspace.getRepository().resolveLocalPath(repoRoot)), commitSha);
                workspace.indexed(commitSha, commitMessage, new Date());
            } else {
                workspace.fail(result.getOutput());
            }
            return repository.save(workspace);
        } catch (RuntimeException ex) {
            workspace.fail(ex.getMessage());
            repository.save(workspace);
            throw ex;
        }
    }

    /**
     * @deprecated 前端未接入分支 workspace 管理 UI，无调用方，预留 API 暂不维护。
     */
    @Deprecated
    @Transactional(readOnly = true)
    public BranchWorkspace get(Long id) {
        return getEntity(id);
    }

    /**
     * 探测分支当前提交是否变化：变化则标记 STALE，否则 touch。
     *
     * <p>事务边界：git rev-parse/log（秒级）在事务外执行，状态回写用 {@link TransactionTemplate} 短事务。
     * 此方法为只读新鲜度探测，保持同步以供调用方立即判断 STALE 状态。</p>
     */
    public BranchWorkspace refresh(Long id) {
        BranchWorkspace workspace = getEntity(id);
        Path repoPath = Path.of(workspace.getRepository().resolveLocalPath(repoRoot));
        String remoteCommitSha = gitRepositoryService.commitSha(repoPath, workspace.getBranchName());
        String commitMessage = remoteCommitSha.equals(workspace.getCommitSha())
                ? null
                : gitRepositoryService.commitMessage(repoPath, remoteCommitSha);
        return updateInTransaction(id, managed -> {
            if (!remoteCommitSha.equals(managed.getCommitSha())) {
                managed.stale(remoteCommitSha, commitMessage);
            } else {
                managed.touch();
            }
        });
    }

    /**
     * @deprecated 前端未接入分支 workspace 管理 UI，无调用方，预留 API 暂不维护。
     */
    @Deprecated
    @Transactional
    public void delete(Long id) {
        BranchWorkspace workspace = getEntity(id);
        Path worktreePath = Path.of(workspace.resolveWorktreePath(worktreeRoot.toString())).toAbsolutePath().normalize();
        Path codegraphIndexPath = effectiveCodegraphIndexPath(workspace);
        FileUtil.ensureUnderRoot(worktreePath, worktreeRoot, "worktree");
        FileUtil.ensureUnderRoot(codegraphIndexPath, worktreePath, "CodeGraph 索引");

        gitRepositoryService.removeWorktree(Path.of(workspace.getRepository().resolveLocalPath(repoRoot)), worktreePath);
        FileUtil.deleteDirectoryIfExists(codegraphIndexPath);
        repository.delete(workspace);
    }

    /**
     * @deprecated 前端未接入分支 workspace 管理 UI，无调用方，预留 API 暂不维护。
     */
    @Deprecated
    @Transactional(readOnly = true)
    public BranchWorkspace getReadyEntity(Long id, Long repositoryId) {
        BranchWorkspace workspace = getEntity(id);
        if (!workspace.getRepository().getId().equals(repositoryId)) {
            throw new ValidationException("分支 workspace 不属于当前仓库");
        }
        if (workspace.getStatus() != BranchWorkspaceStatus.READY) {
            throw new InvalidStateException("分支 workspace 未就绪，请先索引");
        }
        return workspace;
    }

    @Transactional(readOnly = true)
    public BranchWorkspace getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("分支 workspace", id));
    }

    /**
     * 在短事务内按 id 重新加载受管实体并应用变更，避免游离实体 merge 覆盖并发修改。
     */
    private BranchWorkspace updateInTransaction(Long id, Consumer<BranchWorkspace> updater) {
        return transactionTemplate.execute(status -> {
            BranchWorkspace managed = repository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("分支 workspace", id));
            updater.accept(managed);
            return managed;
        });
    }

    private BranchWorkspace createWorkspace(CodeRepository codeRepo, String branchName) {
        return createWorkspace(codeRepo, branchName, null);
    }

    private BranchWorkspace createWorkspace(CodeRepository codeRepo, String branchName, String selectedCommitSha) {
        String commitSha = selectedCommitSha == null || selectedCommitSha.isBlank()
                ? gitRepositoryService.commitSha(Path.of(codeRepo.resolveLocalPath(repoRoot)), branchName)
                : selectedCommitSha.trim();
        String commitMessage = gitRepositoryService.commitMessage(Path.of(codeRepo.resolveLocalPath(repoRoot)), commitSha);
        BranchWorkspace workspace = new BranchWorkspace();
        workspace.setRepository(codeRepo);
        workspace.setBranchName(branchName);
        workspace.setCommitSha(commitSha);
        workspace.setCommitMessage(commitMessage);
        Path worktreePath = worktreePath(codeRepo, branchName);
        // 存储相对路径（repoName/branchName），运行时由 resolveWorktreePath() 拼接
        String relativeWorktreePath = worktreeRoot.relativize(worktreePath).toString();
        workspace.setWorktreePath(relativeWorktreePath);
        workspace.setCodegraphIndexPath(relativeWorktreePath + "/.codegraph");
        workspace.setStatus(BranchWorkspaceStatus.CREATED);
        return workspace;
    }

    private Path worktreePath(CodeRepository codeRepo, String branchName) {
        return worktreeRoot.resolve(FileUtil.safePathPart(codeRepo.getName())).resolve(FileUtil.safePathPart(branchName));
    }

    private Path codegraphIndexPath(Path worktreePath) {
        return worktreePath.resolve(".codegraph").normalize();
    }

    private Path effectiveCodegraphIndexPath(BranchWorkspace workspace) {
        Path worktreePath = Path.of(workspace.resolveWorktreePath(worktreeRoot.toString())).toAbsolutePath().normalize();
        Path actualIndexPath = codegraphIndexPath(worktreePath);
        Path storedIndexPath = workspace.getCodegraphIndexPath() == null || workspace.getCodegraphIndexPath().isBlank()
                ? null
                : Path.of(workspace.resolveCodegraphIndexPath(worktreeRoot.toString()));
        if (!actualIndexPath.equals(storedIndexPath)) {
            log.info("同步分支 workspace CodeGraph 索引路径，workspaceId={}，旧路径={}，新路径={}",
                    workspace.getId(), workspace.getCodegraphIndexPath(), actualIndexPath);
            workspace.setCodegraphIndexPath(actualIndexPath.toString());
        }
        return actualIndexPath;
    }
}
