package cn.welsione.ascoder.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.file.Path;
import java.util.Date;

/**
 * 代码仓库实体，记录被索引的代码仓库信息。
 */
@Entity
@Table(name = "repositories")
@Getter
@Setter
@NoArgsConstructor
public class CodeRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120, unique = true)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String localPath;

    @Column(columnDefinition = "text")
    private String remoteUrl;

    @Column(length = 255)
    private String defaultBranch;

    @Column(length = 255)
    private String authUsername;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(columnDefinition = "text")
    private String authPassword;

    /**
     * 是否已配置 Git 认证凭据（序列化为 hasCredentials 字段）。
     */
    @JsonProperty("hasCredentials")
    public boolean isHasCredentials() {
        return authUsername != null && !authUsername.isBlank()
                && authPassword != null && !authPassword.isBlank();
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RepositoryStatus status = RepositoryStatus.CREATED;

    private Date lastIndexedAt;

    private Date lastPulledAt;

    @Column(columnDefinition = "text")
    private String lastIndexError;

    @Column(columnDefinition = "text")
    private String lastPullError;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public void indexing() {
        this.status = RepositoryStatus.INDEXING;
        this.lastIndexError = null;
        touch();
    }

    public void ready(Date indexedAt) {
        this.status = RepositoryStatus.READY;
        this.lastIndexedAt = indexedAt;
        this.lastIndexError = null;
        touch();
    }

    public void fail(String error) {
        this.status = RepositoryStatus.FAILED;
        this.lastIndexError = error != null && error.length() > 4000 ? error.substring(0, 4000) : error;
        touch();
    }

    public void pulled(Date pulledAt) {
        this.lastPulledAt = pulledAt;
        this.lastPullError = null;
        touch();
    }

    public void pullFailed(String error) {
        this.lastPullError = error != null && error.length() > 4000 ? error.substring(0, 4000) : error;
        touch();
    }

    public void touch() {
        this.updatedAt = new Date();
    }

    /**
     * 将存储的 localPath 解析为运行时绝对路径。
     *
     * <p>DB 中 localPath 仅存储目录名（如 {@code jprompt}），运行时需结合
     * repoRoot 配置拼出完整路径。若 localPath 已是绝对路径（历史数据或外部传入），
     * 则原样返回。</p>
     *
     * @param repoRoot 仓库根目录配置
     * @return 运行时绝对路径字符串
     */
    public String resolveLocalPath(String repoRoot) {
        if (localPath == null || localPath.isBlank()) {
            return Path.of(repoRoot).toAbsolutePath().normalize().toString();
        }
        if (isAbsolutePath(localPath)) {
            return Path.of(localPath).toAbsolutePath().normalize().toString();
        }
        return Path.of(repoRoot).toAbsolutePath().normalize()
                .resolve(localPath).normalize().toString();
    }

    private static boolean isAbsolutePath(String path) {
        return path.startsWith("/") || path.matches("[A-Za-z]:[\\\\/].*");
    }
}
