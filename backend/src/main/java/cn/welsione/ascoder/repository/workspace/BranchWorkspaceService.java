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

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
    private final Path worktreeRoot;

    private final String repoRoot;

    public BranchWorkspaceService(
            BranchWorkspaceJpaRepository repository,
            RepositoryService repositoryService,
            GitRepositoryService gitRepositoryService,
            CodeGraphClient codeGraphClient,
            @Value("${ascoder.worktree-root:./data/worktrees}") String worktreeRoot,
            @Value("${ascoder.repo-root:./data/repos}") String repoRoot
    ) {
        this.repository = repository;
        this.repositoryService = repositoryService;
        this.gitRepositoryService = gitRepositoryService;
        this.codeGraphClient = codeGraphClient;
        this.worktreeRoot = Path.of(worktreeRoot).toAbsolutePath().normalize();
        this.repoRoot = repoRoot;
    }

    @Transactional(readOnly = true)
    public List<BranchWorkspace> list(Long repositoryId) {
        return repositoryId == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByRepository_IdOrderByBranchNameAsc(repositoryId);
    }

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

    @Transactional
    public BranchWorkspace prepare(Long repositoryId, CreateBranchWorkspaceRequest request) {
        return prepare(repositoryId, request, null);
    }

    @Transactional
    public BranchWorkspace prepare(Long repositoryId, CreateBranchWorkspaceRequest request, String selectedCommitSha) {
        CodeRepository codeRepo = repositoryService.getEntity(repositoryId);
        String branchName = request.getBranchName().trim();
        BranchWorkspace workspace = repository.findByRepository_IdAndBranchName(repositoryId, branchName)
                .orElseGet(() -> createWorkspace(codeRepo, branchName, selectedCommitSha));
        workspace.setRepository(codeRepo);
        workspace.preparing();
        repository.saveAndFlush(workspace);

        try {
            String commitSha = selectedCommitSha == null || selectedCommitSha.isBlank()
                    ? gitRepositoryService.commitSha(Path.of(codeRepo.resolveLocalPath(repoRoot)), branchName)
                    : selectedCommitSha.trim();
            String commitMessage = gitRepositoryService.commitMessage(Path.of(codeRepo.resolveLocalPath(repoRoot)), commitSha);
            gitRepositoryService.createOrUpdateDetachedWorktree(
                    Path.of(codeRepo.resolveLocalPath(repoRoot)),
                    branchName,
                    commitSha,
                    Path.of(workspace.resolveWorktreePath(worktreeRoot.toString()))
            );
            workspace.ready(commitSha, commitMessage);
            return repository.save(workspace);
        } catch (RuntimeException ex) {
            workspace.fail(ex.getMessage());
            repository.save(workspace);
            throw new ValidationException(ex.getMessage(), ex);
        }
    }

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
            gitRepositoryService.createOrUpdateDetachedWorktree(
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

    @Transactional(readOnly = true)
    public BranchWorkspace get(Long id) {
        return getEntity(id);
    }

    @Transactional
    public BranchWorkspace refresh(Long id) {
        BranchWorkspace workspace = getEntity(id);
        String commitSha = gitRepositoryService.commitSha(
                Path.of(workspace.getRepository().resolveLocalPath(repoRoot)),
                workspace.getBranchName()
        );
        if (!commitSha.equals(workspace.getCommitSha())) {
            String commitMessage = gitRepositoryService.commitMessage(
                    Path.of(workspace.getRepository().resolveLocalPath(repoRoot)), commitSha);
            workspace.stale(commitSha, commitMessage);
        } else {
            workspace.touch();
        }
        return repository.save(workspace);
    }

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
