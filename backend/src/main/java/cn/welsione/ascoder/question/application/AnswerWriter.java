package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.agent.domain.AnswerEvidence;
import cn.welsione.ascoder.analysis.CodeEvidenceExtractor;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.question.api.QuestionResponse;
import cn.welsione.ascoder.question.domain.Conversation;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import cn.welsione.ascoder.question.persistence.ConversationJpaRepository;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 回答持久化，将 Agent 回答或失败信息写入 Question 并更新会话时间戳。
 */
@Component
@RequiredArgsConstructor
class AnswerWriter {

    private final QuestionJpaRepository repository;
    private final ConversationJpaRepository conversationRepository;
    private final ObjectMapper objectMapper;
    private final CodeEvidenceExtractor codeEvidenceExtractor = new CodeEvidenceExtractor();

    @Transactional
    QuestionResponse succeed(Long questionId, String answer) {
        return succeed(questionId, answer, null, null);
    }

    @Transactional
    QuestionResponse succeed(Long questionId, String answer, String codeContext, String analysisProcess) {
        Question question = repository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("问题", questionId));
        if (question.getStatus() != QuestionStatus.RUNNING) {
            return QuestionResponse.from(question, null, objectMapper);
        }
        String evidenceSource = joinNonBlank(codeContext, analysisProcess, answer);
        String evidenceJson = toEvidenceJson(codeEvidenceExtractor.extract(evidenceSource));
        question.succeed(codeContext, answer, answerSummary(answer), evidenceJson, analysisProcess, null, null);
        touchConversation(question);
        return QuestionResponse.from(repository.save(question), null, objectMapper);
    }

    @Transactional
    QuestionResponse fail(Long questionId, String errorMessage) {
        return fail(questionId, errorMessage, null, null, null);
    }

    @Transactional
    QuestionResponse fail(Long questionId, String errorMessage, String codeContext, String partialAnswer, String analysisProcess) {
        Question question = repository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("问题", questionId));
        if (question.getStatus() != QuestionStatus.RUNNING && question.getStatus() != QuestionStatus.PENDING) {
            return QuestionResponse.from(question, null, objectMapper);
        }
        String answerSummary = partialAnswer == null || partialAnswer.isBlank()
                ? question.getAnswerSummary()
                : partialAnswer.lines().findFirst().orElse("已生成部分回答");
        String answer = partialAnswer == null || partialAnswer.isBlank() ? question.getAnswer() : partialAnswer;
        String process = analysisProcess == null || analysisProcess.isBlank()
                ? question.getAnalysisProcess()
                : analysisProcess;
        question.fail(codeContext, errorMessage, answer, answerSummary, process);
        touchConversation(question);
        return QuestionResponse.from(repository.save(question), null, objectMapper);
    }

    /**
     * 写入运行中的流式进度，便于客户端刷新后回放已完成的 Agent 活动。
     */
    @Transactional
    void updateProgress(Long questionId, String codeContext, String partialAnswer, String analysisProcess) {
        Question question = repository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("问题", questionId));
        if (question.getStatus() != QuestionStatus.RUNNING) {
            return;
        }
        String answer = partialAnswer == null || partialAnswer.isBlank() ? question.getAnswer() : partialAnswer;
        String process = analysisProcess == null || analysisProcess.isBlank()
                ? question.getAnalysisProcess()
                : analysisProcess;
        question.updateProgress(codeContext, answer, process);
        repository.save(question);
    }

    private void touchConversation(Question question) {
        Conversation conversation = question.getConversation();
        if (conversation != null) {
            conversation.touch();
            conversationRepository.save(conversation);
        }
    }

    private String answerSummary(String answer) {
        return answer.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.replaceFirst("^#+\\s*", ""))
                .findFirst()
                .orElse("已生成回答");
    }

    private String joinNonBlank(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private String toEvidenceJson(List<AnswerEvidence> evidence) {
        if (evidence.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException ex) {
            throw new InvalidStateException("序列化回答证据失败", ex);
        }
    }
}
