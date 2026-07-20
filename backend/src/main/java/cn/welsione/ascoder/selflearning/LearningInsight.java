package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 自学习候选洞察，由 Self Learning Agent 从原始记录中整理，等待管理员审核。
 */
@Entity
@Table(name = "learningInsights")
@Getter
@Setter
@NoArgsConstructor
public class LearningInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    private ProjectSpace projectSpace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repositoryId")
    private CodeRepository repository;

    @Column(columnDefinition = "text")
    private String sourceRawEventIdsJson;

    @Column(columnDefinition = "text")
    private String sourceQuestionIdsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private LearningKnowledgeType type = LearningKnowledgeType.QUESTION_ANSWER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningInsightStatus status = LearningInsightStatus.PENDING_REVIEW;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "mediumtext")
    private String summary;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String conclusion;

    @Column(columnDefinition = "mediumtext")
    private String businessContext;

    @Column(columnDefinition = "mediumtext")
    private String glossaryMappingsJson;

    @Column(columnDefinition = "mediumtext")
    private String codeSymbolsJson;

    @Column(columnDefinition = "mediumtext")
    private String warnings;

    @Column(columnDefinition = "text")
    private String applicableScope;

    @Column(columnDefinition = "mediumtext")
    private String evidenceJson;

    @Column(columnDefinition = "mediumtext")
    private String gitProvenanceJson;

    @Column(length = 500)
    private String tags;

    @Column(nullable = false)
    private double confidence;

    private Long reviewerId;

    @Column(columnDefinition = "mediumtext")
    private String reviewComment;

    private Date reviewedAt;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    @Version
    private Long version = 0L;

    public void touch() {
        updatedAt = new Date();
    }
}
