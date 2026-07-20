package cn.welsione.ascoder.question.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 会话实体，聚合同一上下文下的多个问题。
 * <p>
 * 通过 ID 引用项目空间、仓库和分支工作区，不直接持有 repository 模块的实体。
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "projectSpaceId")
    private Long projectSpaceId;

    @Column(name = "repositoryId")
    private Long repositoryId;

    @Column(name = "branchWorkspaceId")
    private Long branchWorkspaceId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 64)
    private String role;

    @Column(length = 255)
    private String branchName;

    @Column(length = 64)
    private String commitSha;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public void touch() {
        this.updatedAt = new Date();
    }
}
