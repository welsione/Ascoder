package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 项目空间术语，沉淀代码名词、业务名词和专业语境说明。
 */
@Entity
@Table(name = "learningTerms")
@Getter
@Setter
@NoArgsConstructor
public class LearningTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    private ProjectSpace projectSpace;

    @Column(nullable = false, length = 160)
    private String term;

    @Column(columnDefinition = "text")
    private String aliasesJson;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String definition;

    @Column(columnDefinition = "text")
    private String scope;

    @Column(columnDefinition = "mediumtext")
    private String examples;

    @Column(nullable = false, length = 64)
    private String source = "manual";

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public void touch() {
        updatedAt = new Date();
    }
}
