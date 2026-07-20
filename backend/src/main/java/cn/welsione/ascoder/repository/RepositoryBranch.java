package cn.welsione.ascoder.repository;

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

import java.util.Date;

/**
 * 仓库分支引用实体，缓存 Git 分支发现结果供项目空间选择。
 */
@Entity
@Table(
        name = "repositoryBranches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_repositoryBranches_repository_refName",
                columnNames = {"repositoryId", "refName"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class RepositoryBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repositoryId", nullable = false)
    @JsonIgnore
    private CodeRepository repository;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 512)
    private String refName;

    @Column(nullable = false, length = 64)
    private String commitSha;

    @Column(length = 120)
    private String remoteName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RepositoryBranchSourceKind sourceKind;

    @Column(nullable = false)
    private boolean active = true;

    private Date lastSeenAt;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public Long getRepositoryId() {
        return repository == null ? null : repository.getId();
    }

    @JsonProperty("repository")
    public String getRepositoryName() {
        return repository == null ? null : repository.getName();
    }

    public void updateFrom(String name, String refName, String commitSha,
                           String remoteName, RepositoryBranchSourceKind sourceKind, Date seenAt) {
        this.name = name;
        this.refName = refName;
        this.commitSha = commitSha;
        this.remoteName = remoteName;
        this.sourceKind = sourceKind;
        this.active = true;
        this.lastSeenAt = seenAt;
        touch();
    }

    public void deactivate() {
        this.active = false;
        touch();
    }

    public void touch() {
        this.updatedAt = new Date();
    }
}
