package cn.welsione.ascoder.repository.workspace;

import cn.welsione.ascoder.common.transaction.EntityUpdater;
import cn.welsione.ascoder.common.FileUtil;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.RepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;

/**
 * 管理仓库分支对应的独立 worktree，并维护该分支可复现分析所需的索引状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchWorkspaceService {

    private static final String ENTITY_NAME = "分支工作区";

    private final BranchWorkspaceJpaRepository repository;
    private final RepositoryService repositoryService;
    private final GitRepositoryService gitRepositoryService;
    private final EntityUpdater entityUpdater;
    private final TransactionTemplate transactionTemplate;

    @Value("${ascoder.worktree-root:./data/worktrees}")
    private String worktreeRoot;

    @Value("${ascoder.repo-root:./data/repos}")
    private String repoRoot;

    public BranchWorkspace prepare(Long repositoryId, CreateBranchWorkspaceRequest request) {
        return prepare(repositoryId, request, null);
    }

    /**
     * 为分支创建/复用 worktree 并标记 PREPARING，再于事务外执行 git worktree 创建，
     * 最后短事务回写 READY/FAILED 状态。
     *
     * <p>事务边界：git worktree 创建（可能耗时）在事务外执行，避免长时间持有数据库连接；
     * DB 状态流转通过 {@link EntityUpdater} 短事务完成。</p>
     *
     * <p><b>禁止在事务上下文中调用</b>：本方法在事务外执行 git 操作，
     * catch 块中先以独立短事务回写 FAILED 状态再抛出业务异常；
     * 若在 {@code @Transactional} 上下文中调用，后续抛出的异常会触发外层事务回滚，
     * 导致 FAILED 状态丢失。方法入口通过 {@link TransactionSynchronizationManager} 断言无活跃事务。</p>
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
                    Path.of(workspace.resolveWorktreePath(worktreeRoot))
            );
            return entityUpdater.updateById(
                    repository, workspace.getId(),
                    managed -> managed.ready(commitSha, commitMessage), ENTITY_NAME);
        } catch (RuntimeException ex) {
            // 独立短事务回写 FAILED 状态：transactionTemplate.execute() 在无外层事务时
            // 开启独立事务，回调正常返回即提交，后续抛出的 ValidationException 不会回滚已提交的状态
            transactionTemplate.executeWithoutResult(status -> {
                BranchWorkspace managed = repository.findById(workspace.getId())
                        .orElseThrow(() -> new ResourceNotFoundException(ENTITY_NAME, workspace.getId()));
                managed.fail(ex.getMessage());
            });
            throw new ValidationException(ex.getMessage(), ex);
        }
    }

    /**
     * 探测分支当前提交是否变化：变化则标记 STALE，否则 touch。
     *
     * <p>事务边界：git rev-parse/log（秒级）在事务外执行，状态回写用独立短事务。
     * 此方法为只读新鲜度探测，保持同步以供调用方立即判断 STALE 状态。</p>
     */
    public BranchWorkspace refresh(Long id) {
        BranchWorkspace workspace = getEntity(id);
        Path repoPath = Path.of(workspace.getRepository().resolveLocalPath(repoRoot));
        String remoteCommitSha = gitRepositoryService.commitSha(repoPath, workspace.getBranchName());
        // 仅当 commit 可能变化时才查询 commitMessage（避免不必要的 git log 调用）
        boolean likelyChanged = !remoteCommitSha.equals(workspace.getCommitSha());
        String commitMessage = likelyChanged
                ? gitRepositoryService.commitMessage(repoPath, remoteCommitSha)
                : null;
        return entityUpdater.updateById(
                repository, id, managed -> {
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
        String relativeWorktreePath = Path.of(worktreeRoot).toAbsolutePath().normalize()
                .relativize(worktreePath).toString();
        workspace.setWorktreePath(relativeWorktreePath);
        workspace.setCodegraphIndexPath(relativeWorktreePath + "/.codegraph");
        workspace.setStatus(BranchWorkspaceStatus.CREATED);
        return workspace;
    }

    private Path worktreePath(CodeRepository codeRepo, String branchName) {
        return Path.of(worktreeRoot).toAbsolutePath().normalize()
                .resolve(FileUtil.safePathPart(codeRepo.getName()))
                .resolve(FileUtil.safePathPart(branchName));
    }
}
