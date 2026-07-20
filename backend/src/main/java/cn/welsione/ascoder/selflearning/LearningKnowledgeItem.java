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
 * 审核后的正式知识，作为后续问答的可追溯历史线索。
 */
@Entity
@Table(name = "learningKnowledgeItems")
@Getter
@Setter
@NoArgsConstructor
public class LearningKnowledgeItem {

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
    private String sourceInsightIdsJson;

    @Column(columnDefinition = "text")
    private String sourceRawEventIdsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private LearningKnowledgeType type = LearningKnowledgeType.QUESTION_ANSWER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningKnowledgeStatus status = LearningKnowledgeStatus.VERIFIED;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String content;

    @Column(columnDefinition = "mediumtext")
    private String summary;

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

    @Column(nullable = false)
    private int usageCount;

    @Column(nullable = false)
    private int acceptedCount;

    @Column(nullable = false)
    private int rejectedCount;

    private Date lastUsedAt;

    @Column(columnDefinition = "text")
    private String staleReason;

    private Long reviewerId;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    @Version
    private Long version = 0L;

    public void markStale(String reason) {
        status = LearningKnowledgeStatus.STALE;
        staleReason = reason;
        touch();
    }

    public void archive() {
        status = LearningKnowledgeStatus.DEPRECATED;
        touch();
    }

    public void touch() {
        updatedAt = new Date();
    }
}
