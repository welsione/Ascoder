package cn.welsione.ascoder.question.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 问题实体，记录用户提交的代码问题和 Agent 的回答。
 * <p>
 * 通过 ID 引用所属的项目空间、仓库和分支工作区，不直接持有 repository 模块的实体，
 * 避免跨聚合的 JPA 关联导致模块耦合与 lazy loading 问题。需要实体信息时由 Service 层按需加载。
 */
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "projectSpaceId")
    private Long projectSpaceId;

    @Column(name = "repositoryId")
    private Long repositoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversationId")
    private Conversation conversation;

    @Column(name = "branchWorkspaceId")
    private Long branchWorkspaceId;

    @Column(name = "logUploadIdsJson", columnDefinition = "varchar(512)")
    private String logUploadIdsJson;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};

    public List<Long> getLogUploadIds() {
        if (logUploadIdsJson == null || logUploadIdsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(logUploadIdsJson, LONG_LIST);
        } catch (JsonProcessingException ex) {
            return Collections.emptyList();
        }
    }

    public void setLogUploadIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            this.logUploadIdsJson = null;
        } else {
            try {
                this.logUploadIdsJson = MAPPER.writeValueAsString(ids);
            } catch (JsonProcessingException ex) {
                this.logUploadIdsJson = null;
            }
        }
    }

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(length = 64)
    private String role;

    @Column(length = 255)
    private String branchName;

    @Column(length = 64)
    private String commitSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuestionStatus status = QuestionStatus.PENDING;

    @Column(columnDefinition = "mediumtext")
    private String answer;

    @Column(columnDefinition = "mediumtext")
    private String answerSummary;

    @Column(columnDefinition = "mediumtext")
    private String answerEvidenceJson;

    @Column(columnDefinition = "mediumtext")
    private String analysisProcess;

    @Column(columnDefinition = "mediumtext")
    private String uncertainty;

    @Column(columnDefinition = "mediumtext")
    private String nextStep;

    @Column(columnDefinition = "mediumtext")
    private String codegraphContext;

    @Column(columnDefinition = "mediumtext")
    private String errorMessage;

    private Date startedAt;

    private Date completedAt;

    @Column(nullable = false)
    private Date createdAt = new Date();

    public void running() {
        this.status = QuestionStatus.RUNNING;
        this.startedAt = new Date();
        this.completedAt = null;
        this.errorMessage = null;
    }

    public void succeed(
            String codegraphContext,
            String answer,
            String answerSummary,
            String answerEvidenceJson,
            String analysisProcess,
            String uncertainty,
            String nextStep
    ) {
        this.status = QuestionStatus.SUCCEEDED;
        this.codegraphContext = codegraphContext;
        this.answer = answer;
        this.answerSummary = answerSummary;
        this.answerEvidenceJson = answerEvidenceJson;
        this.analysisProcess = analysisProcess;
        this.uncertainty = uncertainty;
        this.nextStep = nextStep;
        this.completedAt = new Date();
    }

    public void fail(
            String codegraphContext,
            String errorMessage,
            String answer,
            String answerSummary,
            String analysisProcess
    ) {
        this.status = QuestionStatus.FAILED;
        this.codegraphContext = codegraphContext;
        this.errorMessage = errorMessage;
        this.answer = answer;
        this.answerSummary = answerSummary;
        this.analysisProcess = analysisProcess;
        this.completedAt = new Date();
    }

    public void fail(String codegraphContext, String errorMessage) {
        fail(codegraphContext, errorMessage, this.answer, this.answerSummary, this.analysisProcess);
    }

    /**
     * 保存运行中的流式进度，用于页面刷新后恢复已发生的分析过程。
     */
    public void updateProgress(String codegraphContext, String answer, String analysisProcess) {
        this.codegraphContext = codegraphContext;
        this.answer = answer;
        this.analysisProcess = analysisProcess;
    }
}
