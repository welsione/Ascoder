package cn.welsione.ascoder.repository.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import cn.welsione.ascoder.repository.CodeRepository;
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

/** 分支工作区实体，为某仓库分支创建的 git worktree，承载索引构建的状态与提交信息。 */
@Entity
@Table(
        name = "branchWorkspaces",
        uniqueConstraints = @UniqueConstraint(name = "uk_branchWorkspaces_repository_branch", columnNames = {
                "repositoryId", "branchName"
        })
)
@Getter
@Setter
@NoArgsConstructor
public class BranchWorkspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repositoryId", nullable = false)
    @JsonIgnore
    private CodeRepository repository;

    @JsonProperty("branch")
    @Column(nullable = false, length = 255)
    private String branchName;

    @Column(nullable = false, length = 64)
    private String commitSha;

    @Column(length = 512)
    private String commitMessage;

    @Column(nullable = false, columnDefinition = "text")
    private String worktreePath;

    @Column(nullable = false, columnDefinition = "text")
    private String codegraphIndexPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BranchWorkspaceStatus status = BranchWorkspaceStatus.CREATED;

    private Date lastIndexedAt;

    @Column(columnDefinition = "text")
    private String lastIndexError;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public void preparing() {
        this.status = BranchWorkspaceStatus.PREPARING;
        this.lastIndexError = null;
        touch();
    }

    public void ready(String commitSha, String commitMessage) {
        this.status = BranchWorkspaceStatus.READY;
        this.commitSha = commitSha;
        this.commitMessage = commitMessage;
        this.lastIndexError = null;
        touch();
    }

    public void indexing() {
        this.status = BranchWorkspaceStatus.INDEXING;
        this.lastIndexError = null;
        touch();
    }

    public void indexed(String commitSha, String commitMessage, Date indexedAt) {
        this.status = BranchWorkspaceStatus.READY;
        this.commitSha = commitSha;
        this.commitMessage = commitMessage;
        this.lastIndexedAt = indexedAt;
        this.lastIndexError = null;
        touch();
    }

    public void stale(String commitSha, String commitMessage) {
        this.status = BranchWorkspaceStatus.STALE;
        this.commitSha = commitSha;
        this.commitMessage = commitMessage;
        touch();
    }

    public void fail(String errorMessage) {
        this.status = BranchWorkspaceStatus.FAILED;
        this.lastIndexError = errorMessage;
        touch();
    }

    public void touch() {
        this.updatedAt = new Date();
    }

    public Long getRepositoryId() {
        return repository == null ? null : repository.getId();
    }

    @JsonProperty("repository")
    public String getRepositoryName() {
        return repository == null ? null : repository.getName();
    }

    /**
     * 将存储的 worktreePath 解析为运行时绝对路径。
     *
     * <p>DB 中 worktreePath 存储形式如 {@code repoName/branchName}，运行时
     * 需结合 worktreeRoot 配置拼出完整路径。若已是绝对路径则原样返回。</p>
     */
    public String resolveWorktreePath(String worktreeRoot) {
        if (worktreePath == null || worktreePath.isBlank()) {
            return null;
        }
        if (isAbsolutePath(worktreePath)) {
            return Path.of(worktreePath).toAbsolutePath().normalize().toString();
        }
        return Path.of(worktreeRoot).toAbsolutePath().normalize()
                .resolve(worktreePath).normalize().toString();
    }

    /**
     * 解析 CodeGraph 索引路径：worktreePath + "/.codegraph"。
     */
    public String resolveCodegraphIndexPath(String worktreeRoot) {
        String wt = resolveWorktreePath(worktreeRoot);
        if (wt == null) {
            return null;
        }
        return Path.of(wt).resolve(".codegraph").normalize().toString();
    }

    private static boolean isAbsolutePath(String path) {
        return path.startsWith("/") || path.matches("[A-Za-z]:[\\\\/].*");
    }
}
