package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.question.domain.Question;
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
 * 用户纠错记录，保存经验证的错误修正和防止重复犯错的依据。
 */
@Entity
@Table(name = "learningCorrections")
@Getter
@Setter
@NoArgsConstructor
public class LearningCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    private ProjectSpace projectSpace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sourceQuestionId")
    private Question sourceQuestion;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String wrongConclusion;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String correctedConclusion;

    @Column(columnDefinition = "mediumtext")
    private String verificationProcess;

    @Column(columnDefinition = "mediumtext")
    private String evidenceJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LearningCorrectionStatus status = LearningCorrectionStatus.CANDIDATE;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public void verify() {
        status = LearningCorrectionStatus.VERIFIED;
        touch();
    }

    public void reject() {
        status = LearningCorrectionStatus.REJECTED;
        touch();
    }

    public void touch() {
        updatedAt = new Date();
    }
}
