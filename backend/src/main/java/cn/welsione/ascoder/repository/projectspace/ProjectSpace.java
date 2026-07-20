package cn.welsione.ascoder.repository.projectspace;

import cn.welsione.ascoder.repository.project.Project;
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

/** 项目空间实体，基于项目下多个仓库分支构建的隔离工作区，承载准备、索引等生命周期状态。 */
@Entity
@Table(
        name = "projectSpaces",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_projectSpaces_project_name",
                columnNames = {"projectId", "name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ProjectSpace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectId", nullable = false)
    @JsonIgnore
    private Project project;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, columnDefinition = "text")
    private String rootPath;

    @Column(columnDefinition = "text")
    private String codegraphIndexPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectSpaceStatus status = ProjectSpaceStatus.CREATED;

    private Date lastPreparedAt;

    private Date lastIndexedAt;

    @Column(columnDefinition = "mediumtext")
    private String lastError;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public Long getProjectId() {
        return project == null ? null : project.getId();
    }

    @JsonProperty("project")
    public String getProjectName() {
        return project == null ? null : project.getName();
    }

    public void touch() {
        this.updatedAt = new Date();
    }

    public void preparing() {
        this.status = ProjectSpaceStatus.PREPARING;
        this.lastError = null;
        touch();
    }

    public void readyToIndex(Date preparedAt) {
        this.status = ProjectSpaceStatus.READY_TO_INDEX;
        this.lastPreparedAt = preparedAt;
        this.lastError = null;
        touch();
    }

    public void indexing() {
        this.status = ProjectSpaceStatus.INDEXING;
        this.lastError = null;
        touch();
    }

    public void indexed(Date indexedAt) {
        this.status = ProjectSpaceStatus.READY;
        this.lastIndexedAt = indexedAt;
        this.lastError = null;
        touch();
    }

    private static final int MAX_ERROR_LENGTH = 60000;

    public void stale(String errorMessage) {
        this.status = ProjectSpaceStatus.STALE;
        this.lastError = truncate(errorMessage);
        touch();
    }

    public void fail(String errorMessage) {
        this.status = ProjectSpaceStatus.FAILED;
        this.lastError = truncate(errorMessage);
        touch();
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) + "..." : message;
    }

    /**
     * 将存储的 rootPath（目录名）解析为运行时绝对路径。
     *
     * <p>DB 中 rootPath 仅存储空间目录名（如 {@code qys-private-hotfix_5.5.x}），
     * 运行时需结合 projectSpaceRoot 配置拼出完整路径。若 rootPath 已是
     * 绝对路径（历史数据），则原样返回。</p>
     *
     * @param projectSpaceRoot 项目空间根目录配置
     * @return 运行时绝对路径字符串
     */
    public String resolveRootPath(String projectSpaceRoot) {
        if (rootPath == null || rootPath.isBlank()) {
            return Path.of(projectSpaceRoot).toAbsolutePath().normalize().toString();
        }
        if (isAbsolutePath(rootPath)) {
            return Path.of(rootPath).toAbsolutePath().normalize().toString();
        }
        return Path.of(projectSpaceRoot).toAbsolutePath().normalize()
                .resolve(rootPath).normalize().toString();
    }

    /**
     * 解析 CodeGraph 索引路径：rootPath + "/.codegraph"。
     *
     * @param projectSpaceRoot 项目空间根目录配置
     * @return CodeGraph 索引绝对路径字符串
     */
    public String resolveCodegraphIndexPath(String projectSpaceRoot) {
        return Path.of(resolveRootPath(projectSpaceRoot)).resolve(".codegraph").normalize().toString();
    }

    private static boolean isAbsolutePath(String path) {
        return path.startsWith("/") || path.matches("[A-Za-z]:[\\\\/].*");
    }
}
