package cn.welsione.ascoder.repository.projectspace.task;

import cn.welsione.ascoder.common.FileUtil;
import cn.welsione.ascoder.common.task.TaskDefinition;
import cn.welsione.ascoder.common.task.TaskKind;
import cn.welsione.ascoder.common.task.TaskProgress;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMember;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMemberJpaRepository;
import cn.welsione.ascoder.repository.workspace.BranchWorkspace;
import cn.welsione.ascoder.repository.workspace.BranchWorkspaceService;
import cn.welsione.ascoder.repository.workspace.CreateBranchWorkspaceRequest;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.Map;

/**
 * 项目空间准备异步任务定义，负责为项目空间的所有成员仓库执行 clone/fetch/checkout 并创建符号链接。
 *
 * <p>上下文仅包含 projectSpaceId 字段。执行过程中逐个准备成员仓库，
 * 每完成一个成员更新进度百分比。成功后更新实体状态为 READY_TO_INDEX。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSpacePrepareTaskDefinition implements TaskDefinition<Map<String, String>> {

    private static final TypeReference<Map<String, String>> CONTEXT_TYPE = new TypeReference<>() {};

    private final ProjectSpaceJpaRepository projectSpaceJpaRepository;
    private final ProjectSpaceMemberJpaRepository memberJpaRepository;
    private final BranchWorkspaceService branchWorkspaceService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ascoder.project-space-root:./data/project-spaces}")
    private String projectSpaceRoot;

    @Value("${ascoder.worktree-root:./data/worktrees}")
    private String worktreeRoot;

    @Override
    public TaskKind kind() {
        return TaskKind.PROJECT_SPACE_PREPARE;
    }

    @Override
    public void execute(Map<String, String> context, TaskProgress progress) throws Exception {
        Long projectSpaceId = Long.valueOf(context.get("projectSpaceId"));

        log.info("开始项目空间准备任务，projectSpaceId={}", projectSpaceId);

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
                prepareMember(snapshot.projectSpaceId, rootPath, member);
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
     */
    private void prepareMember(Long projectSpaceId, Path rootPath, MemberSnapshot member) throws Exception {
        BranchWorkspace branchWorkspace = branchWorkspaceService.prepare(
                member.repositoryId,
                new CreateBranchWorkspaceRequest(member.branchName),
                member.commitSha
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
    public String serializeContext(Map<String, String> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("序列化项目空间准备任务上下文失败", e);
        }
    }

    @Override
    public Map<String, String> deserializeContext(String json) {
        try {
            return objectMapper.readValue(json, CONTEXT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化项目空间准备任务上下文失败", e);
        }
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
