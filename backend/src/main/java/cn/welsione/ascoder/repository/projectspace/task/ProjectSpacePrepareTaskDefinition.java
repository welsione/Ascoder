package cn.welsione.ascoder.repository.projectspace.task;

import cn.welsione.ascoder.common.FileUtil;
import cn.welsione.ascoder.common.task.TaskDefinition;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.common.task.TaskContextSerializer;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.RepositoryService;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMember;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMemberJpaRepository;
import cn.welsione.ascoder.repository.workspace.BranchWorkspace;
import cn.welsione.ascoder.repository.workspace.BranchWorkspaceService;
import cn.welsione.ascoder.repository.workspace.CreateBranchWorkspaceRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

/**
 * 项目空间准备异步任务定义，负责为项目空间的所有成员仓库执行 clone/fetch/checkout 并创建符号链接。
 *
 * <p>上下文为 {@link ProjectSpacePrepareContext}，仅携带 projectSpaceId。执行过程中逐个准备成员仓库，
 * 每完成一个成员更新进度百分比。成功后更新实体状态为 READY_TO_INDEX。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSpacePrepareTaskDefinition implements TaskDefinition<ProjectSpacePrepareContext> {

    private final ProjectSpaceJpaRepository projectSpaceJpaRepository;
    private final ProjectSpaceMemberJpaRepository memberJpaRepository;
    private final BranchWorkspaceService branchWorkspaceService;
    private final GitRepositoryService gitRepositoryService;
    private final RepositoryService repositoryService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ascoder.project-space-root:./data/project-spaces}")
    private String projectSpaceRoot;

    @Value("${ascoder.repo-root:./data/repos}")
    private String repoRoot;

    @Value("${ascoder.worktree-root:./data/worktrees}")
    private String worktreeRoot;

    @Override
    public TaskKind kind() {
        return TaskKind.PROJECT_SPACE_PREPARE;
    }

    @Override
    public long defaultTimeoutMs() {
        return 120 * 60 * 1000L; // 2 小时（多成员仓库逐个准备）
    }

    @Override
    public String resolveBusinessLabel(Long businessId) {
        return projectSpaceJpaRepository.findById(businessId)
                .map(space -> space.getName() + " (项目空间)")
                .orElse(null);
    }

    @Override
    public void execute(ProjectSpacePrepareContext context, TaskProgress progress) throws Exception {
        Long projectSpaceId = context.getProjectSpaceId();
        boolean advanceToRemote = context.isAdvanceToRemote();

        log.info("开始项目空间准备任务，projectSpaceId={}，advanceToRemote={}", projectSpaceId, advanceToRemote);

        // 在事务内读取项目空间及成员快照
        PrepareSnapshot snapshot = readSnapshot(projectSpaceId);
        Path rootPath = snapshot.rootPath;

        Files.createDirectories(rootPath);

        List<MemberSnapshot> members = snapshot.members;
        int total = members.size();

        for (int i = 0; i < total; i++) {
            progress.checkCancelled();

            MemberSnapshot member = members.get(i);
            int percent = (i + 1) * 100 / total;
            progress.update(percent, "准备成员 " + (i + 1) + "/" + total + "：" + member.alias);

            try {
                prepareMember(snapshot.projectSpaceId, rootPath, member, advanceToRemote);
            } catch (Exception ex) {
                String errorMessage = "准备成员 " + member.alias + " 失败：" + ex.getMessage();
                log.error("项目空间准备任务失败，projectSpaceId={}，member={}", projectSpaceId, member.alias, ex);

                transactionTemplate.executeWithoutResult(status -> {
                    ProjectSpace space = projectSpaceJpaRepository.findById(projectSpaceId)
                            .orElseThrow(() -> new IllegalStateException("项目空间不存在，id=" + projectSpaceId));
                    space.fail(errorMessage);
                    projectSpaceJpaRepository.save(space);
                });

                throw new RuntimeException(errorMessage, ex);
            }
        }

        // 全部成员准备成功，更新实体状态
        transactionTemplate.executeWithoutResult(status -> {
            ProjectSpace space = projectSpaceJpaRepository.findById(projectSpaceId)
                    .orElseThrow(() -> new IllegalStateException("项目空间不存在，id=" + projectSpaceId));
            space.readyToIndex(new Date());
            projectSpaceJpaRepository.save(space);
        });

        progress.update(100, "准备完成");
        log.info("项目空间准备任务完成，projectSpaceId={}", projectSpaceId);
    }

    /**
     * 在事务内读取项目空间和成员的快照数据，避免在事务外持有懒加载代理。
     */
    private PrepareSnapshot readSnapshot(Long projectSpaceId) {
        return transactionTemplate.execute(status -> {
            ProjectSpace space = projectSpaceJpaRepository.findById(projectSpaceId)
                    .orElseThrow(() -> new IllegalStateException("项目空间不存在，id=" + projectSpaceId));

            Path rootPath = Path.of(space.resolveRootPath(projectSpaceRoot)).toAbsolutePath().normalize();

            List<MemberSnapshot> memberSnapshots = memberJpaRepository
                    .findByProjectSpace_IdOrderByCreatedAtAsc(projectSpaceId)
                    .stream()
                    .map(m -> new MemberSnapshot(
                            m.getId(),
                            m.getRepositoryId(),
                            m.getBranchName(),
                            m.getCommitSha(),
                            m.getAlias()
                    ))
                    .toList();

            return new PrepareSnapshot(projectSpaceId, rootPath, memberSnapshots);
        });
    }

    /**
     * 准备单个成员：创建/更新分支 worktree 并建立符号链接。
     *
     * @param advanceToRemote true 时将 worktree 推进到 {@code origin/<branch>} 最新 commit
     *                         （拉取更新场景）；false 时 checkout 到 {@code member.commitSha}（创建/重新准备场景）
     */
    private void prepareMember(Long projectSpaceId, Path rootPath, MemberSnapshot member, boolean advanceToRemote)
            throws Exception {
        String selectedCommitSha = member.commitSha;
        if (advanceToRemote) {
            CodeRepository repo = repositoryService.getEntity(member.repositoryId);
            Path repoPath = Path.of(repo.resolveLocalPath(repoRoot));
            String remoteSha = gitRepositoryService.remoteCommitSha(repoPath, member.branchName);
            if (remoteSha != null && !remoteSha.isBlank()) {
                selectedCommitSha = remoteSha;
            } else {
                log.warn("远端分支 {} 无对应引用，回退到成员 commit，member={}", member.branchName, member.alias);
            }
        }

        BranchWorkspace branchWorkspace = branchWorkspaceService.prepare(
                member.repositoryId,
                new CreateBranchWorkspaceRequest(member.branchName),
                selectedCommitSha
        );

        Path linkPath = rootPath.resolve(member.alias).normalize();
        FileUtil.ensureUnderRoot(linkPath, rootPath, "项目空间成员路径");
        Path worktreePath = Path.of(branchWorkspace.resolveWorktreePath(worktreeRoot))
                .toAbsolutePath().normalize();

        createOrReplaceLink(linkPath, worktreePath);

        // 保存成员的 worktree 关联和 commit 信息
        transactionTemplate.executeWithoutResult(status -> {
            ProjectSpace space = projectSpaceJpaRepository.findById(projectSpaceId)
                    .orElseThrow(() -> new IllegalStateException("项目空间不存在，id=" + projectSpaceId));
            ProjectSpaceMember entity = memberJpaRepository.findById(member.id)
                    .orElseThrow(() -> new IllegalStateException("项目空间成员不存在，id=" + member.id));
            entity.setBranchWorkspace(branchWorkspace);
            entity.setCommitSha(branchWorkspace.getCommitSha());
            entity.setCommitMessage(branchWorkspace.getCommitMessage());
            entity.setLinkPath(linkPath.toString());
            entity.touch();
            memberJpaRepository.save(entity);
            space.touch();
            projectSpaceJpaRepository.save(space);
        });
    }

    /**
     * 创建或替换符号链接（Unix）或 junction（Windows）。
     */
    private void createOrReplaceLink(Path linkPath, Path targetPath) throws Exception {
        if (!Files.exists(targetPath)) {
            throw new IllegalStateException("worktree 目录不存在：" + targetPath);
        }
        if (Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)) {
            FileUtil.deleteDirectoryIfExists(linkPath);
        }
        if (isWindows()) {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "mklink", "/J",
                    linkPath.toString(), targetPath.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("创建 junction 失败：" + linkPath + " -> " + targetPath);
            }
        } else {
            Path relativeTarget = linkPath.getParent().relativize(targetPath.toAbsolutePath().normalize());
            Files.createSymbolicLink(linkPath, relativeTarget);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Override
    public String serializeContext(ProjectSpacePrepareContext context) {
        return TaskContextSerializer.serialize(objectMapper, context, "项目空间准备");
    }

    @Override
    public ProjectSpacePrepareContext deserializeContext(String json) {
        return TaskContextSerializer.deserialize(objectMapper, json, ProjectSpacePrepareContext.class, "项目空间准备");
    }

    /** 准备任务快照，携带项目空间 ID、根路径和成员列表。 */
    private static class PrepareSnapshot {
        final Long projectSpaceId;
        final Path rootPath;
        final List<MemberSnapshot> members;

        PrepareSnapshot(Long projectSpaceId, Path rootPath, List<MemberSnapshot> members) {
            this.projectSpaceId = projectSpaceId;
            this.rootPath = rootPath;
            this.members = members;
        }
    }

    /** 成员快照，携带准备所需的业务字段。 */
    private static class MemberSnapshot {
        final Long id;
        final Long repositoryId;
        final String branchName;
        final String commitSha;
        final String alias;

        MemberSnapshot(Long id, Long repositoryId, String branchName, String commitSha, String alias) {
            this.id = id;
            this.repositoryId = repositoryId;
            this.branchName = branchName;
            this.commitSha = commitSha;
            this.alias = alias;
        }
    }
}
