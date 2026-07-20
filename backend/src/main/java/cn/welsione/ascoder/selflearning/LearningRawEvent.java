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
 * 自学习原始记录，保存问答、Agent 输出、工具调用、用户反馈和证据留痕。
 */
@Entity
@Table(name = "learningRawEvents")
@Getter
@Setter
@NoArgsConstructor
public class LearningRawEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projectSpaceId", nullable = false)
    private ProjectSpace projectSpace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repositoryId")
    private CodeRepository repository;

    @Column(length = 160)
    private String branchName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questionId")
    private Question question;

    private Long conversationId;

    @Column(length = 80)
    private String agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private LearningRawEventType eventType;

    @Column(columnDefinition = "mediumtext")
    private String eventPayloadJson;

    @Column(columnDefinition = "mediumtext")
    private String summary;

    @Column(columnDefinition = "mediumtext")
    private String evidenceJson;

    @Column(columnDefinition = "mediumtext")
    private String gitProvenanceJson;

    @Column(length = 48)
    private String userFeedbackType;

    private Date sourceCreatedAt;

    @Column(nullable = false)
    private Date createdAt = new Date();
}
