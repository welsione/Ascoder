package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.question.domain.Question;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 项目空间沉淀的经验知识，保存经过治理的问答经验、约定、业务语境和错误案例。
 */
@Entity
@Table(name = "learningExperiences")
@Getter
@Setter
@NoArgsConstructor
public class LearningExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    private ProjectSpace projectSpace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repositoryId")
    private CodeRepository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sourceQuestionId")
    private Question sourceQuestion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private LearningExperienceType type = LearningExperienceType.QUESTION_ANSWER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningExperienceStatus status = LearningExperienceStatus.CANDIDATE;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "mediumtext")
    private String problem;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String conclusion;

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

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public void verify() {
        status = LearningExperienceStatus.VERIFIED;
        if (confidence < 0.7) {
            confidence = 0.7;
        }
        touch();
    }

    public void reject() {
        status = LearningExperienceStatus.REJECTED;
        rejectedCount++;
        touch();
    }

    public void archive() {
        status = LearningExperienceStatus.DEPRECATED;
        touch();
    }

    public void touch() {
        updatedAt = new Date();
    }
}
