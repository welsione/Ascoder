package cn.welsione.ascoder.repository.workspace;

import cn.welsione.ascoder.common.TransactionalEntityUpdater;
import cn.welsione.ascoder.common.FileUtil;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.RepositoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;

/**
 * 管理仓库分支对应的独立 worktree，并维护该分支可复现分析所需的索引状态。
 */
@Service
public class BranchWorkspaceService {

    private static final String ENTITY_NAME = "分支 workspace";

    private final BranchWorkspaceJpaRepository repository;
    private final RepositoryService repositoryService;
    private final GitRepositoryService gitRepositoryService;
    private final TransactionTemplate transactionTemplate;
    private final Path worktreeRoot;

    private final String repoRoot;

    public BranchWorkspaceService(
            BranchWorkspaceJpaRepository repository,
            RepositoryService repositoryService,
            GitRepositoryService gitRepositoryService,
            TransactionTemplate transactionTemplate,
            @Value("${ascoder.worktree-root:./data/worktrees}") String worktreeRoot,
            @Value("${ascoder.repo-root:./data/repos}") String repoRoot
    ) {
        this.repository = repository;
        this.repositoryService = repositoryService;
        this.gitRepositoryService = gitRepositoryService;
        this.transactionTemplate = transactionTemplate;
        this.worktreeRoot = Path.of(worktreeRoot).toAbsolutePath().normalize();
        this.repoRoot = repoRoot;
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
            return TransactionalEntityUpdater.updateById(
                    transactionTemplate, repository, workspace.getId(),
                    managed -> managed.ready(commitSha, commitMessage), ENTITY_NAME);
        } catch (RuntimeException ex) {
            TransactionalEntityUpdater.updateById(
                    transactionTemplate, repository, workspace.getId(),
                    managed -> managed.fail(ex.getMessage()), ENTITY_NAME);
            throw new ValidationException(ex.getMessage(), ex);
        }
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
        return TransactionalEntityUpdater.updateById(
                transactionTemplate, repository, id, managed -> {
                    if (!remoteCommitSha.equals(managed.getCommitSha())) {
                        managed.stale(remoteCommitSha, commitMessage);
                    } else {
                        managed.touch();
                    }
                }, ENTITY_NAME);
    }

    @Transactional(readOnly = true)
    public BranchWorkspace getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY_NAME, id));
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
}
