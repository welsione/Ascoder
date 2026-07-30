package cn.welsione.ascoder.repository.workspace;

import cn.welsione.ascoder.common.transaction.EntityUpdater;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;

/**
 * 管理仓库分支对应的独立 worktree，并维护该分支可复现分析所需的索引状态。
 */
@Slf4j
@Service
public class BranchWorkspaceService {

    private static final String ENTITY_NAME = "分支工作区";

    private final BranchWorkspaceJpaRepository repository;
    private final RepositoryService repositoryService;
    private final GitRepositoryService gitRepositoryService;
    private final EntityUpdater entityUpdater;
    private final Path worktreeRoot;

    private final String repoRoot;

    public BranchWorkspaceService(
            BranchWorkspaceJpaRepository repository,
            RepositoryService repositoryService,
            GitRepositoryService gitRepositoryService,
            EntityUpdater entityUpdater,
            @Value("${ascoder.worktree-root:./data/worktrees}") String worktreeRoot,
            @Value("${ascoder.repo-root:./data/repos}") String repoRoot
    ) {
        this.repository = repository;
        this.repositoryService = repositoryService;
        this.gitRepositoryService = gitRepositoryService;
        this.entityUpdater = entityUpdater;
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
     * DB 状态流转通过 {@link EntityUpdater} 独立短事务完成。</p>
     *
     * <p><b>禁止在事务上下文中调用</b>：本方法在事务外执行 git 操作，
     * catch 块中先以独立短事务回写 FAILED 状态再抛出业务异常。
     * 方法入口通过 {@link TransactionSynchronizationManager} 断言无活跃事务；
     * {@link EntityUpdater} 使用 {@code PROPAGATION_REQUIRES_NEW} 确保回写独立提交，
     * 不受外层事务回滚影响。</p>
     *
     * @throws ValidationException git worktree 创建失败，调用方禁止在事务上下文中捕获此异常
     */
    public BranchWorkspace prepare(Long repositoryId, CreateBranchWorkspaceRequest request, String selectedCommitSha) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new InvalidStateException("prepare 禁止在事务上下文中调用，请移除调用方的 @Transactional");
        }
        CodeRepository codeRepo = repositoryService.getEntity(repositoryId);
        String branchName = request.getBranchName().trim();
        BranchWorkspace workspace = repository.findByRepository_IdAndBranchName(repositoryId, branchName)
                .orElseGet(() -> createWorkspace(codeRepo, branchName, selectedCommitSha));
        workspace.setRepository(codeRepo);
        workspace.preparing();
        entityUpdater.save(repository::saveAndFlush, workspace);

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
            return entityUpdater.updateById(
                    repository::findById, repository::save, workspace.getId(),
                    managed -> managed.ready(commitSha, commitMessage), ENTITY_NAME);
        } catch (RuntimeException ex) {
            // 复用 EntityUpdater port 回写 FAILED 状态：PROPAGATION_REQUIRES_NEW 独立事务，
            // 回调返回即提交，后续抛出的 ValidationException 不会回滚已提交的状态
            entityUpdater.updateById(
                    repository::findById, repository::save, workspace.getId(),
                    managed -> managed.fail(ex.getMessage()), ENTITY_NAME);
            throw new ValidationException(ex.getMessage(), ex);
        }
    }

    /**
     * 探测分支当前提交是否变化：变化则标记 STALE，否则 touch。
     *
     * <p>事务边界：git rev-parse/log（秒级）在事务外执行，状态回写用 {@link EntityUpdater} 独立短事务。
     * 此方法为只读新鲜度探测，保持同步以供调用方立即判断 STALE 状态。</p>
     *
     * <p>并发说明：事务外读取的 {@code workspace.getCommitSha()} 与回调内重新加载的
     * {@code managed.getCommitSha()} 可能因并发修改而不一致，回调内以 {@code managed} 为准做最终判断。
     * commitMessage 在事务外按"可能变化"预查询，存在极小概率的并发窗口：
     * 若预查询后 managed 的 commitSha 被并发改为与 remoteCommitSha 相同，则预查询的
     * commitMessage 不会被使用（回调走 touch 分支）。这是可接受的权衡--避免在事务内调用 git log
     * 带来的连接持有开销，commitMessage 作为辅助信息即使偶发丢弃也不影响 STALE 状态正确性。</p>
     */
    public BranchWorkspace refresh(Long id) {
        BranchWorkspace workspace = getEntity(id);
        Path repoPath = Path.of(workspace.getRepository().resolveLocalPath(repoRoot));
        String remoteCommitSha = gitRepositoryService.commitSha(repoPath, workspace.getBranchName());
        // 仅当 commit 可能变化时才查询 commitMessage（避免不必要的 git log 调用）
        String commitMessage = remoteCommitSha.equals(workspace.getCommitSha())
                ? null
                : gitRepositoryService.commitMessage(repoPath, remoteCommitSha);
        return entityUpdater.updateById(
                repository::findById, repository::save, id, managed -> {
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
        return worktreeRoot
                .resolve(FileUtil.safePathPart(codeRepo.getName()))
                .resolve(FileUtil.safePathPart(branchName));
    }
}
