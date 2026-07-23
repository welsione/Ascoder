package cn.welsione.ascoder.repository.projectspace;

import cn.welsione.ascoder.repository.workspace.BranchWorkspace;
import cn.welsione.ascoder.repository.workspace.BranchWorkspaceService;
import cn.welsione.ascoder.codegraph.infrastructure.cli.IndexProgressTracker;
import cn.welsione.ascoder.common.FileUtil;
import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.task.TaskEngine;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskSubmitRequest;
import cn.welsione.ascoder.question.application.QuestionRunningGuard;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.RepositoryBranch;
import cn.welsione.ascoder.repository.RepositoryBranchService;
import cn.welsione.ascoder.repository.project.Project;
import cn.welsione.ascoder.repository.project.ProjectRepository;
import cn.welsione.ascoder.repository.project.ProjectService;
import cn.welsione.ascoder.repository.git.GitCommitInfo;
import cn.welsione.ascoder.repository.git.GitCredentialStore;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 项目空间领域服务，负责空间的创建、成员准备、索引编排及状态流转。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectSpaceService {

    private final ProjectSpaceJpaRepository repository;
    private final ProjectSpaceMemberJpaRepository memberRepository;
    private final ProjectService projectService;
    private final BranchWorkspaceService branchWorkspaceService;
    private final RepositoryBranchService repositoryBranchService;
    private final GitRepositoryService gitRepositoryService;
    private final GitCredentialStore gitCredentialStore;
    private final IndexProgressTracker indexProgressTracker;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final QuestionRunningGuard questionRunningGuard;
    private final TaskEngine taskEngine;

    @Value("${ascoder.project-space-root:./data/project-spaces}")
    private String projectSpaceRoot;

    @Value("${ascoder.repo-root:./data/repos}")
    private String repoRoot;

    @Value("${ascoder.worktree-root:./data/worktrees}")
    private String worktreeRoot;

    @Transactional(readOnly = true)
    public List<ProjectSpace> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public ProjectSpace get(Long id) {
        return getEntity(id);
    }

    @Transactional(readOnly = true)
    public List<ProjectSpaceMember> members(Long projectSpaceId) {
        getEntity(projectSpaceId);
        return memberRepository.findByProjectSpace_IdOrderByCreatedAtAsc(projectSpaceId);
    }

    @Transactional(readOnly = true)
    public List<ProjectSpaceMemberResponse> memberResponses(Long projectSpaceId) {
        return members(projectSpaceId).stream()
                .map(this::memberResponse)
                .toList();
    }

    @Transactional
    public ProjectSpace create(CreateProjectSpaceRequest request) {
        Project project = projectService.getEntity(request.getProjectId());
        String name = request.getName().trim();
        if (repository.existsByProject_IdAndName(project.getId(), name)) {
            throw new DuplicateException("项目空间名称已存在");
        }

        List<ProjectRepository> projectMembers = projectService.members(project.getId());
        if (projectMembers.isEmpty()) {
            throw new InvalidStateException("项目还没有配置仓库");
        }

        ProjectSpace space = new ProjectSpace();
        space.setProject(project);
        space.setName(name);
        space.setDescription(trimToNull(request.getDescription()));
        space.setStatus(ProjectSpaceStatus.CREATED);
        space.setRootPath(rootPath(project.getName(), name));

        try {
            ProjectSpace saved = repository.saveAndFlush(space);
            createMembers(saved, projectMembers, request);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateException("项目空间名称或成员目录别名冲突", ex);
        }
    }

    public ProjectSpace prepare(Long id) {
        ProjectSpacePrepareSnapshot snapshot = beginPrepare(id);

        Map<String, String> context = new LinkedHashMap<>();
        context.put("projectSpaceId", id.toString());
        TaskSubmitRequest<Map<String, String>> request = new TaskSubmitRequest<>();
        request.setKind(TaskKind.PROJECT_SPACE_PREPARE);
        request.setContext(context);
        request.setBusinessId(id);
        taskEngine.submit(request);
        log.info("已提交项目空间准备异步任务，projectSpaceId={}", id);

        return transactionTemplate.execute(status -> getEntity(id));
    }

    public ProjectSpace index(Long id) {
        ProjectSpaceIndexSnapshot snapshot = beginIndex(id, false);
        boolean previouslyIndexed = snapshot.isPreviouslyIndexed();

        TaskKind kind = previouslyIndexed ? TaskKind.CODEGRAPH_SYNC : TaskKind.CODEGRAPH_INDEX;
        Map<String, String> context = new LinkedHashMap<>();
        context.put("repositoryPath", snapshot.getRootPath().toString());
        context.put("projectSpaceId", id.toString());
        if (!previouslyIndexed) {
            context.put("codegraphIndexPath", snapshot.getCodegraphIndexPath().toString());
        }
        TaskSubmitRequest<Map<String, String>> request = new TaskSubmitRequest<>();
        request.setKind(kind);
        request.setContext(context);
        request.setBusinessId(id);
        taskEngine.submit(request);
        log.info("已提交 CodeGraph {} 异步任务，projectSpaceId={}",
                previouslyIndexed ? "增量同步" : "全量索引", id);

        return transactionTemplate.execute(status -> getEntity(id));
    }

    /**
     * 重新索引：先删除现有 {@code .codegraph} 索引目录，再执行全量索引。
     *
     * <p>与 {@link #index(Long)} 的区别：index 会根据 lastIndexedAt 自动选择增量 sync 或全量 index，
     * 而 reindex 始终删除旧索引后全量重建。用于索引损坏、结构变更等需要强制重建的场景。
     * 放行 FAILED 状态（作为恢复路径），因此不能直接复用 {@link #beginIndex(Long, boolean)} 的默认状态集合。</p>
     */
    public ProjectSpace reindex(Long id) {
        ProjectSpaceIndexSnapshot snapshot = beginIndex(id, true);

        Map<String, String> context = new LinkedHashMap<>();
        context.put("repositoryPath", snapshot.getRootPath().toString());
        context.put("codegraphIndexPath", snapshot.getCodegraphIndexPath().toString());
        context.put("projectSpaceId", id.toString());
        context.put("isReindex", "true");
        TaskSubmitRequest<Map<String, String>> request = new TaskSubmitRequest<>();
        request.setKind(TaskKind.CODEGRAPH_INDEX);
        request.setContext(context);
        request.setBusinessId(id);
        taskEngine.submit(request);
        log.info("已提交 CodeGraph 重新索引异步任务，projectSpaceId={}", id);

        return transactionTemplate.execute(status -> getEntity(id));
    }

    @Transactional
    public ProjectSpace refresh(Long id) {
        ProjectSpace space = getEntity(id);
        if (space.getStatus() == ProjectSpaceStatus.PREPARING || space.getStatus() == ProjectSpaceStatus.INDEXING) {
            throw new InvalidStateException("项目空间正在处理中");
        }

        List<String> staleReasons = new ArrayList<>();
        Path rootPath = managedRootPath(space);
        if (!Files.isDirectory(rootPath)) {
            staleReasons.add("项目空间目录不存在：" + rootPath);
        }

        List<ProjectSpaceMember> members = memberRepository.findByProjectSpace_IdOrderByCreatedAtAsc(space.getId());
        if (members.isEmpty()) {
            staleReasons.add("项目空间没有成员仓库");
        }
        for (ProjectSpaceMember member : members) {
            inspectMemberFreshness(member, rootPath, staleReasons);
        }

        if (staleReasons.isEmpty()) {
            space.touch();
            return repository.save(space);
        }
        space.stale(String.join("\n", staleReasons));
        return repository.save(space);
    }

    /**
     * 拉取项目空间所有成员仓库的远端引用，并重新计算空间是否落后。
     */
    public ProjectSpace pullRemote(Long id) {
        ensureNoRunningQuestions(id);
        upsertMemberCredentials(id);

        List<CodeRepository> memberRepos = distinctMemberRepositories(id);
        for (CodeRepository repo : memberRepos) {
            Map<String, String> context = new LinkedHashMap<>();
            context.put("repositoryPath", repo.resolveLocalPath(repoRoot));
            context.put("repositoryId", repo.getId().toString());
            context.put("operation", "fetch");
            context.put("authUsername", repo.getAuthUsername());
            context.put("authPassword", repo.getAuthPassword());
            context.put("remoteUrl", repo.getRemoteUrl());
            TaskSubmitRequest<Map<String, String>> fetchRequest = new TaskSubmitRequest<>();
            fetchRequest.setKind(TaskKind.GIT_FETCH);
            fetchRequest.setContext(context);
            fetchRequest.setBusinessId(repo.getId());
            taskEngine.submit(fetchRequest);
            log.info("已提交 Git fetch 异步任务（pullRemote），repositoryId={}", repo.getId());
        }

        return refreshInTransaction(id);
    }

    /**
     * 删除项目空间。
     *
     * <p>BUG-8 修复：先删目录，再删 DB。如果目录删除失败立即抛异常，事务回滚，
     * 避免出现"DB 已无记录但磁盘残留鬼影目录"的不一致状态。</p>
     *
     * <p>跨聚合引用清理改用 {@link ProjectSpaceDeletedEvent} 领域事件：通过
     * BEFORE_COMMIT 阶段同事务监听，由各聚合（Question / Conversation）自行处理
     * 自己对项目空间的引用，避免本服务越界操作其他聚合。</p>
     */
    @Transactional
    public void delete(Long id) {
        ProjectSpace space = getEntity(id);
        deleteSpace(space);
    }

    /** 删除项目下所有项目空间，供 Project 删除时级联调用。 */
    public void deleteByProjectId(Long projectId) {
        List<ProjectSpace> spaces = repository.findByProject_Id(projectId);
        for (ProjectSpace space : spaces) {
            deleteSpace(space);
        }
    }

    private void deleteSpace(ProjectSpace space) {
        if (space.getStatus() == ProjectSpaceStatus.PREPARING || space.getStatus() == ProjectSpaceStatus.INDEXING) {
            throw new InvalidStateException("项目空间「" + space.getName() + "」正在处理中，无法删除");
        }
        ensureNoRunningQuestions(space.getId());

        Path rootPath = managedRootPath(space);
        FileUtil.deleteDirectoryIfExists(rootPath);

        eventPublisher.publishEvent(new ProjectSpaceDeletedEvent(space.getId()));

        List<ProjectSpaceMember> members = memberRepository.findByProjectSpace_IdOrderByCreatedAtAsc(space.getId());
        memberRepository.deleteAll(members);
        repository.delete(space);
    }

    @Transactional(readOnly = true)
    public ProjectSpace getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("项目空间", id));
    }

    private void createMembers(
            ProjectSpace space,
            List<ProjectRepository> projectMembers,
            CreateProjectSpaceRequest request
    ) {
        Map<Long, CreateProjectSpaceRequest.MemberBranchRequest> overrides = request.getMembers() == null
                ? Map.of()
                : request.getMembers().stream().collect(Collectors.toMap(
                        CreateProjectSpaceRequest.MemberBranchRequest::getRepositoryId,
                        Function.identity(),
                        (left, right) -> right
                ));

        for (ProjectRepository projectMember : projectMembers) {
            CreateProjectSpaceRequest.MemberBranchRequest override = overrides.get(projectMember.getRepositoryId());
            RepositoryBranch selectedBranch = selectedBranch(projectMember.getRepositoryId(), override);
            String branchName = selectedBranch == null
                    ? defaultText(override == null ? null : override.getBranchName(), request.getDefaultBranch())
                    : selectedBranch.getName();
            String alias = FileUtil.safePathPart(
                    override == null ? projectMember.getAlias() : defaultText(override.getAlias(), projectMember.getAlias())
            );
            ProjectSpaceMember member = new ProjectSpaceMember();
            member.setProjectSpace(space);
            member.setRepository(projectMember.getRepository());
            member.setBranch(selectedBranch);
            member.setBranchName(branchName);
            if (selectedBranch != null) {
                member.setBranchRefName(selectedBranch.getRefName());
                member.setBranchSourceKind(selectedBranch.getSourceKind());
                member.setCommitSha(selectedBranch.getCommitSha());
            }
            member.setAlias(alias);
            member.setRole(override == null ? projectMember.getRole() : defaultText(override.getRole(), projectMember.getRole()));
            member.setLinkPath(space.getRootPath() + "/" + alias);
            memberRepository.save(member);
        }
    }

    private ProjectSpacePrepareSnapshot beginPrepare(Long id) {
        return transactionTemplate.execute(status -> {
            ProjectSpace space = getEntity(id);
            if (space.getStatus() == ProjectSpaceStatus.PREPARING || space.getStatus() == ProjectSpaceStatus.INDEXING) {
                throw new InvalidStateException("项目空间正在处理中");
            }
            ensureNoRunningQuestions(id);

            space.preparing();
            repository.saveAndFlush(space);

            List<ProjectSpaceMember> members = memberRepository.findByProjectSpace_IdOrderByCreatedAtAsc(space.getId());
            if (members.isEmpty()) {
                throw new IllegalStateException("项目空间没有成员仓库");
            }
            List<ProjectSpaceMemberSnapshot> memberSnapshots = members.stream()
                    .map(member -> new ProjectSpaceMemberSnapshot(
                            member.getId(),
                            member.getRepositoryId(),
                            member.getBranchName(),
                            member.getCommitSha(),
                            member.getAlias()
                    ))
                    .toList();
            return new ProjectSpacePrepareSnapshot(space.getId(), managedRootPath(space), memberSnapshots);
        });
    }

    /**
     * 索引前置校验与状态流转。
     *
     * @param id 项目空间 ID
     * @param allowFailed 是否放行 FAILED 状态（重新索引作为恢复路径时为 true）
     */
    private ProjectSpaceIndexSnapshot beginIndex(Long id, boolean allowFailed) {
        return transactionTemplate.execute(status -> {
            ProjectSpace space = getEntity(id);
            if (space.getStatus() == ProjectSpaceStatus.PREPARING || space.getStatus() == ProjectSpaceStatus.INDEXING) {
                throw new InvalidStateException("项目空间正在处理中");
            }
            if (!isIndexable(space.getStatus(), allowFailed)) {
                throw new InvalidStateException("项目空间尚未准备代码");
            }
            ensureNoRunningQuestions(id);

            Path rootPath = managedRootPath(space);
            if (!Files.isDirectory(rootPath)) {
                throw new InvalidStateException("项目空间目录不存在，请先准备代码");
            }

            space.indexing();
            repository.saveAndFlush(space);
            indexProgressTracker.start(id);
            boolean previouslyIndexed = space.getLastIndexedAt() != null;
            return new ProjectSpaceIndexSnapshot(
                    space.getId(),
                    rootPath,
                    Path.of(space.resolveCodegraphIndexPath(projectSpaceRoot)).toAbsolutePath().normalize(),
                    previouslyIndexed
            );
        });
    }

    /**
     * 判断当前状态是否可执行索引。
     *
     * <p>普通索引仅允许 READY_TO_INDEX/READY/STALE；重新索引额外放行 FAILED 作为恢复路径。</p>
     */
    private boolean isIndexable(ProjectSpaceStatus status, boolean allowFailed) {
        if (status == ProjectSpaceStatus.READY_TO_INDEX
                || status == ProjectSpaceStatus.READY
                || status == ProjectSpaceStatus.STALE) {
            return true;
        }
        return allowFailed && status == ProjectSpaceStatus.FAILED;
    }

    /**
     * 获取项目空间中去重后的成员仓库列表。
     */
    private List<CodeRepository> distinctMemberRepositories(Long id) {
        return transactionTemplate.execute(status -> memberRepository.findByProjectSpace_IdOrderByCreatedAtAsc(id)
                .stream()
                .map(ProjectSpaceMember::getRepository)
                .collect(Collectors.toMap(CodeRepository::getId, Function.identity(), (left, right) -> left))
                .values()
                .stream()
                .toList());
    }

    private ProjectSpace refreshInTransaction(Long id) {
        return transactionTemplate.execute(status -> refresh(id));
    }

    private void ensureNoRunningQuestions(Long projectSpaceId) {
        if (questionRunningGuard.hasRunningQuestion(projectSpaceId)) {
            throw new InvalidStateException("项目空间仍有运行中的问题，请等待回答完成后再操作");
        }
    }

    /**
     * 将项目空间中所有成员仓库的凭据写入 credential store。
     *
     * <p>若仓库的 remoteUrl 不是 HTTPS URL（可能是本地路径或相对路径），
     * 则从仓库的 .git/config 中读取实际的远程 origin URL。</p>
     */
    private void upsertMemberCredentials(Long id) {
        transactionTemplate.executeWithoutResult(status -> {
            memberRepository.findByProjectSpace_IdOrderByCreatedAtAsc(id).stream()
                    .map(ProjectSpaceMember::getRepository)
                    .collect(Collectors.toMap(CodeRepository::getId, Function.identity(), (left, right) -> left))
                    .values()
                    .stream()
                    .filter(repo -> repo.getAuthUsername() != null && repo.getAuthPassword() != null)
                    .forEach(repo -> {
                        String remoteUrl = repo.getRemoteUrl();
                        if (remoteUrl == null || !remoteUrl.startsWith("https://")) {
                            remoteUrl = resolveOriginUrl(repo);
                        }
                        if (remoteUrl != null && !remoteUrl.isBlank()) {
                            gitCredentialStore.upsert(remoteUrl, repo.getAuthUsername(), repo.getAuthPassword());
                        }
                    });
        });
    }

    /**
     * 从仓库的 .git/config 中读取 origin 的远程 URL。
     */
    private String resolveOriginUrl(CodeRepository repo) {
        try {
            return gitRepositoryService.getRemoteUrl(Path.of(repo.resolveLocalPath(repoRoot)));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @lombok.Value
    private static class ProjectSpacePrepareSnapshot {
        Long projectSpaceId;
        Path rootPath;
        List<ProjectSpaceMemberSnapshot> members;
    }

    @lombok.Value
    private static class ProjectSpaceMemberSnapshot {
        Long id;
        Long repositoryId;
        String branchName;
        String commitSha;
        String alias;
    }

    @lombok.Value
    private static class ProjectSpaceIndexSnapshot {
        Long projectSpaceId;
        Path rootPath;
        Path codegraphIndexPath;
        boolean previouslyIndexed;
    }

    private void inspectMemberFreshness(ProjectSpaceMember member, Path rootPath, List<String> staleReasons) {
        Path linkPath = member.getLinkPath() == null || member.getLinkPath().isBlank()
                ? rootPath.resolve(member.getAlias()).normalize()
                : Path.of(member.resolveLinkPath(projectSpaceRoot)).toAbsolutePath().normalize();
        FileUtil.ensureUnderRoot(linkPath, rootPath, "项目空间成员路径");
        if (!Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)) {
            staleReasons.add(member.getAlias() + " 链接目录不存在：" + linkPath);
        } else if (!Files.isDirectory(linkPath)) {
            staleReasons.add(member.getAlias() + " 不是目录：" + linkPath);
        }

        if (member.getBranchWorkspace() == null) {
            staleReasons.add(member.getAlias() + " 尚未绑定分支 worktree");
            return;
        }
        BranchWorkspace branchWorkspace = branchWorkspaceService.refresh(member.getBranchWorkspaceId());
        if (member.getCommitSha() == null || !member.getCommitSha().equals(branchWorkspace.getCommitSha())) {
            staleReasons.add("%s commit 已变化：%s -> %s".formatted(
                    member.getAlias(),
                    defaultText(member.getCommitSha(), "未记录"),
                    branchWorkspace.getCommitSha()
            ));
        }
    }

    private void createOrReplaceLink(Path linkPath, Path targetPath) throws Exception {
        if (!Files.exists(targetPath)) {
            throw new IllegalStateException("worktree 目录不存在：" + targetPath);
        }
        if (Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)) {
            FileUtil.deleteDirectoryIfExists(linkPath);
        }
        if (isWindows()) {
            // Windows: use junction (mklink /J) instead of symlink.
            // Junction resolves to a local path without symlink traversal,
            // so CodeGraph CLI does not trigger "Path traversal blocked".
            // Junction does not require admin privileges unlike directory symlinks.
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "mklink", "/J",
                    linkPath.toString(), targetPath.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("创建 junction 失败：" + linkPath + " -> " + targetPath);
            }
        } else {
            // Unix：软链接 target 用相对路径，使其在本地开发与 Docker 部署间可移植
            // （project-spaces 与 worktrees 同在 data/ 下，相对关系在两种环境一致）。
            Files.createSymbolicLink(linkPath, relativeLinkTarget(linkPath, targetPath));
        }
    }

    /**
     * 计算软链接相对 target：以 linkPath 所在目录为基准指向 targetPath。
     *
     * <p>相对 target 使软链接可跨环境移植（本地 {@code <project>/data/...} 与 Docker {@code /app/data/...}），
     * 避免绝对路径在环境切换后失效。Windows junction 不支持相对路径，仅 Unix 软链接使用。</p>
     */
    static Path relativeLinkTarget(Path linkPath, Path targetPath) {
        return linkPath.getParent().relativize(targetPath.toAbsolutePath().normalize());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private ProjectSpaceMemberResponse memberResponse(ProjectSpaceMember member) {
        String currentCommitMessage = member.getCommitMessage();
        if ((currentCommitMessage == null || currentCommitMessage.isBlank()) && member.getCommitSha() != null) {
            currentCommitMessage = gitRepositoryService.commitMessage(
                    Path.of(member.getRepository().resolveLocalPath(repoRoot)),
                    member.getCommitSha()
            );
        }
        String remoteCommitSha = gitRepositoryService.remoteCommitSha(
                Path.of(member.getRepository().resolveLocalPath(repoRoot)),
                member.getBranchName()
        );
        String remoteCommitMessage = remoteCommitSha == null
                ? null
                : gitRepositoryService.commitMessage(Path.of(member.getRepository().resolveLocalPath(repoRoot)), remoteCommitSha);
        boolean behindRemote = member.getCommitSha() != null
                && remoteCommitSha != null
                && !member.getCommitSha().equals(remoteCommitSha);
        return ProjectSpaceMemberResponse.from(
                member,
                currentCommitMessage,
                remoteCommitSha,
                remoteCommitMessage,
                behindRemote,
                recentCommitResponses(member)
        );
    }

    private List<ProjectSpaceCommitResponse> recentCommitResponses(ProjectSpaceMember member) {
        String ref = member.getCommitSha() == null || member.getCommitSha().isBlank()
                ? member.getBranchName()
                : member.getCommitSha();
        try {
            List<GitCommitInfo> commits = gitRepositoryService.recentCommits(
                    Path.of(member.getRepository().resolveLocalPath(repoRoot)),
                    ref,
                    5
            );
            return commits.stream().map(ProjectSpaceCommitResponse::from).toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private Path managedRootPath(ProjectSpace space) {
        Path root = Path.of(projectSpaceRoot).toAbsolutePath().normalize();
        Path path = Path.of(space.resolveRootPath(projectSpaceRoot)).toAbsolutePath().normalize();
        FileUtil.ensureUnderRoot(path, root, "项目空间目录");
        return path;
    }

    /**
     * 计算项目空间的目录名（basename），不含任何前缀路径。
     */
    private String rootPath(String projectName, String spaceName) {
        return FileUtil.safePathPart(projectName) + "-" + FileUtil.safePathPart(spaceName);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private RepositoryBranch selectedBranch(Long repositoryId, CreateProjectSpaceRequest.MemberBranchRequest override) {
        if (override == null || override.getBranchId() == null) {
            return null;
        }
        return repositoryBranchService.getActiveEntity(override.getBranchId(), repositoryId);
    }
}
