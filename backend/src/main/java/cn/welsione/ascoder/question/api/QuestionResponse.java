package cn.welsione.ascoder.question.api;

import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import cn.welsione.ascoder.question.planning.QuestionType;
import cn.welsione.ascoder.loganalysis.domain.LogFile;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.Date;
import java.util.List;

/**
 * 问题的响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {
    Long id;
    Long conversationId;
    String conversationTitle;
    Long projectSpaceId;
    String projectSpaceName;
    Long repositoryId;
    String repositoryName;
    Long branchWorkspaceId;
    String branchName;
    String commitSha;
    String text;
    String role;
    QuestionStatus status;
    String answer;
    String answerSummary;
    List<AnswerEvidenceResponse> answerEvidence;
    String analysisProcess;
    String uncertainty;
    String nextStep;
    String codegraphContext;
    List<Long> logUploadIds;
    List<LogUploadBriefResponse> logUploads;
    QueryPlanResponse queryPlan;
    String errorMessage;
    Date startedAt;
    Date completedAt;
    Date createdAt;

    public static QuestionResponse from(Question question, QueryPlan queryPlan, ObjectMapper objectMapper) {
        return from(question, queryPlan, null, null, List.of(), objectMapper);
    }

    public static QuestionResponse from(Question question, QueryPlan queryPlan,
                                        List<LogUploadBriefResponse> logUploads,
                                        ObjectMapper objectMapper) {
        return from(question, queryPlan, null, null, logUploads, objectMapper);
    }

    /**
     * 构建响应 DTO。projectSpaceName / repositoryName 由调用方按需查询后传入，
     * 因为 Question 实体仅持有 ID，不再直接关联 repository 模块实体。
     */
    public static QuestionResponse from(Question question, QueryPlan queryPlan,
                                        String projectSpaceName, String repositoryName,
                                        List<LogUploadBriefResponse> logUploads,
                                        ObjectMapper objectMapper) {
        return new QuestionResponse(
                question.getId(),
                question.getConversation() == null ? null : question.getConversation().getId(),
                question.getConversation() == null ? null : question.getConversation().getTitle(),
                question.getProjectSpaceId(),
                projectSpaceName,
                question.getRepositoryId(),
                repositoryName,
                question.getBranchWorkspaceId(),
                question.getBranchName(),
                question.getCommitSha(),
                question.getText(),
                question.getRole(),
                question.getStatus(),
                question.getAnswer(),
                question.getAnswerSummary(),
                AnswerEvidenceResponse.fromJson(question.getAnswerEvidenceJson(), objectMapper),
                question.getAnalysisProcess(),
                question.getUncertainty(),
                question.getNextStep(),
                question.getCodegraphContext(),
                question.getLogUploadIds(),
                logUploads,
                QueryPlanResponse.from(queryPlan, objectMapper),
                question.getErrorMessage(),
                question.getStartedAt(),
                question.getCompletedAt(),
                question.getCreatedAt()
        );
    }

    /**
     * 问题关联的日志上传摘要，用于历史问题卡片展示附件信息。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogUploadBriefResponse {
        Long id;
        String originalFilename;
        String fileType;
        long fileSize;
        String status;
        List<String> fileNames;

        public static LogUploadBriefResponse from(LogUpload upload, List<LogFile> files) {
            List<String> names = files == null ? List.of() : files.stream()
                    .map(LogFile::getDisplayName)
                    .toList();
            return new LogUploadBriefResponse(
                    upload.getId(),
                    upload.getOriginalFilename(),
                    upload.getFileType(),
                    upload.getFileSize() == null ? 0L : upload.getFileSize(),
                    upload.getStatus().name(),
                    names
            );
        }
    }

    /**
     * 回答证据响应 DTO。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerEvidenceResponse {
        String title;
        String reference;
        String detail;

        private static final TypeReference<List<AnswerEvidenceResponse>> EVIDENCE_LIST = new TypeReference<>() {
        };

        public static List<AnswerEvidenceResponse> fromJson(String json, ObjectMapper objectMapper) {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            try {
                return objectMapper.readValue(json, EVIDENCE_LIST);
            } catch (JsonProcessingException ex) {
                return List.of();
            }
        }
    }

    /**
     * 查询规划的响应 DTO。
     */
    @Value
    @AllArgsConstructor
    public static class QueryPlanResponse {
        Long id;
        Long questionId;
        QuestionType type;
        List<String> rewrittenQueries;
        List<String> recommendedTools;
        List<String> recommendedSkills;
        double confidence;
        List<String> matchedSignals;
        List<QuestionType> alternativeTypes;
        String reasoning;
        Date createdAt;

        private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
        };
        private static final TypeReference<List<QuestionType>> QUESTION_TYPE_LIST = new TypeReference<>() {
        };

        public static QueryPlanResponse from(QueryPlan queryPlan, ObjectMapper objectMapper) {
            if (queryPlan == null) {
                return null;
            }
            return new QueryPlanResponse(
                    queryPlan.getId(),
                    queryPlan.getQuestion().getId(),
                    queryPlan.getType(),
                    readList(objectMapper, queryPlan.getRewrittenQueriesJson()),
                    readList(objectMapper, queryPlan.getRecommendedToolsJson()),
                    readList(objectMapper, queryPlan.getRecommendedSkillsJson()),
                    queryPlan.getConfidence(),
                    readList(objectMapper, queryPlan.getMatchedSignalsJson()),
                    readQuestionTypes(objectMapper, queryPlan.getAlternativeTypesJson()),
                    queryPlan.getReasoning(),
                    queryPlan.getCreatedAt()
            );
        }

        private static List<String> readList(ObjectMapper objectMapper, String json) {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            try {
                return objectMapper.readValue(json, STRING_LIST);
            } catch (Exception ex) {
                return List.of();
            }
        }

        private static List<QuestionType> readQuestionTypes(ObjectMapper objectMapper, String json) {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            try {
                return objectMapper.readValue(json, QUESTION_TYPE_LIST);
            } catch (Exception ex) {
                return List.of();
            }
        }
    }
}
