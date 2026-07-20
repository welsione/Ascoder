package cn.welsione.ascoder.repository.projectspace;

import cn.welsione.ascoder.common.FileUtil;
import cn.welsione.ascoder.repository.workspace.BranchWorkspace;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.RepositoryBranch;
import cn.welsione.ascoder.repository.RepositoryBranchSourceKind;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.file.Path;
import java.util.Date;

/** 项目空间成员实体，描述空间内某个仓库分支及其分支工作区、别名与提交信息。 */
@Entity
@Table(
        name = "projectSpaceMembers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_projectSpaceMembers_space_repository",
                        columnNames = {"projectSpaceId", "repositoryId"}
                ),
                @UniqueConstraint(
                        name = "uk_projectSpaceMembers_space_alias",
                        columnNames = {"projectSpaceId", "alias"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProjectSpaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    @JsonIgnore
    private ProjectSpace projectSpace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repositoryId", nullable = false)
    @JsonIgnore
    private CodeRepository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branchWorkspaceId")
    @JsonIgnore
    private BranchWorkspace branchWorkspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branchId")
    @JsonIgnore
    private RepositoryBranch branch;

    @Column(nullable = false, length = 255)
    private String branchName;

    @Column(length = 512)
    private String branchRefName;

    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private RepositoryBranchSourceKind branchSourceKind;

    @Column(nullable = false, length = 120)
    private String alias;

    @Column(nullable = false, length = 64)
    private String role = "repository";

    @Column(length = 64)
    private String commitSha;

    @Column(length = 512)
    private String commitMessage;

    @Column(columnDefinition = "text")
    private String linkPath;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public Long getProjectSpaceId() {
        return projectSpace == null ? null : projectSpace.getId();
    }

    @JsonProperty("space")
    public String getProjectSpaceName() {
        return projectSpace == null ? null : projectSpace.getName();
    }

    public Long getRepositoryId() {
        return repository == null ? null : repository.getId();
    }

    @JsonProperty("repository")
    public String getRepositoryName() {
        return repository == null ? null : repository.getName();
    }

    public Long getBranchWorkspaceId() {
        return branchWorkspace == null ? null : branchWorkspace.getId();
    }

    public Long getBranchId() {
        return branch == null ? null : branch.getId();
    }

    public String getWorktreePath() {
        return branchWorkspace == null ? null : branchWorkspace.getWorktreePath();
    }

    /**
     * 将存储的 linkPath（相对路径）解析为运行时绝对路径。
     *
     * <p>DB 中 linkPath 存储形式如 {@code spaceName/alias}，运行时需结合
     * projectSpaceRoot 配置拼出完整路径。若已是绝对路径，则原样返回。</p>
     */
    public String resolveLinkPath(String projectSpaceRoot) {
        return FileUtil.resolveUnderRoot(linkPath, Path.of(projectSpaceRoot));
    }

    public void touch() {
        this.updatedAt = new Date();
    }
}
