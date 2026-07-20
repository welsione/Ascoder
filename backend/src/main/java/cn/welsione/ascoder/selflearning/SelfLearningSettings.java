package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 项目空间自学习设置，控制原始记录、候选洞察和正式知识注入策略。
 */
@Entity
@Table(name = "selfLearningSettings")
@Getter
@Setter
@NoArgsConstructor
public class SelfLearningSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    private ProjectSpace projectSpace;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean autoCandidateEnabled;

    @Column(nullable = false)
    private boolean rawEventCaptureEnabled = true;

    @Column(nullable = false)
    private boolean autoInsightEnabled;

    @Column(nullable = false)
    private boolean answerInjectionEnabled;

    @Column(nullable = false)
    private boolean sourceVisibleEnabled = true;

    @Column(nullable = false)
    private boolean adminReviewRequired = true;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public void touch() {
        updatedAt = new Date();
    }
}
