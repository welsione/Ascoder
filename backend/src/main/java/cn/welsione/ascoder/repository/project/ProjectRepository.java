package cn.welsione.ascoder.repository.project;

import cn.welsione.ascoder.repository.CodeRepository;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 项目仓库成员实体，描述项目与代码仓库的关联及别名、角色、排序等信息。 */
@Entity
@Table(
        name = "projectRepositories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_projectRepositories_project_repository",
                        columnNames = {"projectId", "repositoryId"}
                ),
                @UniqueConstraint(
                        name = "uk_projectRepositories_project_alias",
                        columnNames = {"projectId", "alias"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProjectRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectId", nullable = false)
    @JsonIgnore
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repositoryId", nullable = false)
    @JsonIgnore
    private CodeRepository repository;

    @Column(nullable = false, length = 120)
    private String alias;

    @Column(nullable = false, length = 64)
    private String role = "repository";

    @Column(nullable = false)
    private boolean primaryRepository;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Date createdAt = new Date();

    public Long getProjectId() {
        return project == null ? null : project.getId();
    }

    @JsonProperty("project")
    public String getProjectName() {
        return project == null ? null : project.getName();
    }

    public Long getRepositoryId() {
        return repository == null ? null : repository.getId();
    }

    @JsonProperty("repository")
    public String getRepositoryName() {
        return repository == null ? null : repository.getName();
    }

    public String getDefaultBranch() {
        return repository == null ? null : repository.getDefaultBranch();
    }
}
