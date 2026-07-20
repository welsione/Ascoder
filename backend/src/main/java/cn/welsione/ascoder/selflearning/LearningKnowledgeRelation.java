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
 * 正式知识之间或知识与代码引用之间的关系。
 */
@Entity
@Table(name = "learningKnowledgeRelations")
@Getter
@Setter
@NoArgsConstructor
public class LearningKnowledgeRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    private ProjectSpace projectSpace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sourceKnowledgeItemId")
    private LearningKnowledgeItem sourceKnowledgeItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "targetKnowledgeItemId")
    private LearningKnowledgeItem targetKnowledgeItem;

    @Column(nullable = false, length = 64)
    private String relationType;

    @Column(length = 64)
    private String sourceRefType;

    @Column(length = 500)
    private String sourceRefValue;

    @Column(length = 64)
    private String targetRefType;

    @Column(length = 500)
    private String targetRefValue;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();
}
