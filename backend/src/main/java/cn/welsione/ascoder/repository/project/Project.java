package cn.welsione.ascoder.repository.project;

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

/** 项目实体，聚合一组代码仓库的基本单元。 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120, unique = true)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * 归属用户 ID。NULL 表示系统级公共资源，所有用户可见；管理员可看所有。
     */
    private Long userId;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public void touch() {
        this.updatedAt = new Date();
    }
}
