package cn.welsione.ascoder.loganalysis.application;

import cn.welsione.ascoder.agent.domain.AgentAnswer;
import cn.welsione.ascoder.agent.domain.AnswerEvidence;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.loganalysis.domain.LogAnalysisTask;
import cn.welsione.ascoder.loganalysis.domain.LogAnalysisTaskStatus;
import cn.welsione.ascoder.loganalysis.domain.LogEvidenceRef;
import cn.welsione.ascoder.loganalysis.domain.LogFile;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import cn.welsione.ascoder.loganalysis.persistence.LogAnalysisTaskJpaRepository;
import cn.welsione.ascoder.loganalysis.persistence.LogEvidenceRefJpaRepository;
import cn.welsione.ascoder.loganalysis.persistence.LogFileJpaRepository;
import cn.welsione.ascoder.loganalysis.persistence.LogUploadJpaRepository;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志分析任务编排服务，负责：
 * <ul>
 *     <li>问题入口创建 LogAnalysisTask（与 Question 一一对应）</li>
 *     <li>问题回答完成后写回任务状态与结果摘要</li>
 *     <li>从 Agent 回答证据中提取日志片段，落库为 LogEvidenceRef，便于前端定位</li>
 * </ul>
 *
 * <p>该服务在 QuestionService 已经处于事务边界外被调用，自身使用 {@code @Transactional} 确保数据一致性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogAnalysisService {

    /** 形如 "fileId=12, lines=120-145" 或 "L120-L145" 的日志引用模式，由 Agent 工具输出约定。 */
    private static final Pattern LINE_RANGE = Pattern.compile(
            "(?:fileId\\s*=\\s*(\\d+).{0,40}?)?(?:lines?\\s*=\\s*|L)(\\d+)\\s*[-~]\\s*L?(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private final LogAnalysisTaskJpaRepository taskRepository;
    private final LogEvidenceRefJpaRepository evidenceRepository;
    private final LogUploadJpaRepository uploadRepository;
    private final LogFileJpaRepository logFileRepository;
    private final LogMaskingService maskingService;
    private final ObjectMapper objectMapper;
    private final ProjectSpaceService projectSpaceService;

    /** 在问题入口为关联 logUploadId 的问题创建一条任务记录。 */
    @Transactional
    public LogAnalysisTask startTask(Question question, Long logUploadId) {
        LogUpload upload = uploadRepository.findById(logUploadId)
                .orElseThrow(() -> new ResourceNotFoundException("日志上传不存在：" + logUploadId));
        LogAnalysisTask task = new LogAnalysisTask();
        task.setQuestion(question);
        task.setProjectSpace(question.getProjectSpaceId() == null
                ? null
                : projectSpaceService.getEntity(question.getProjectSpaceId()));
        task.setUpload(upload);
        task.setStatus(LogAnalysisTaskStatus.RUNNING);
        task.setSummaryJson(upload.getSummaryJson());
        return taskRepository.save(task);
    }

    /** 问题回答成功后写回任务结果，并从证据中提取日志引用。 */
    @Transactional
    public void completeOnAnswer(Long questionId, AgentAnswer answer) {
        taskRepository.findByQuestion_Id(questionId).ifPresent(task -> {
            String resultJson = serializeResult(answer);
            task.succeed(resultJson);
            taskRepository.save(task);
            extractEvidence(task, answer);
        });
    }

    /** 问题失败时同步标记任务失败。 */
    @Transactional
    public void failOnAnswer(Long questionId, String errorMessage) {
        taskRepository.findByQuestion_Id(questionId).ifPresent(task -> {
            task.fail(errorMessage == null ? "{}" : errorMessage);
            taskRepository.save(task);
        });
    }

    private String serializeResult(AgentAnswer answer) {
        Map<String, Object> data = Map.of(
                "summary", nullSafe(answer.getSummary()),
                "uncertainty", nullSafe(answer.getUncertainty()),
                "nextStep", nullSafe(answer.getNextStep()),
                "evidenceCount", answer.getEvidence() == null ? 0 : answer.getEvidence().size()
        );
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            log.warn("序列化任务结果失败 questionId 已记录", ex);
            return "{}";
        }
    }

    private void extractEvidence(LogAnalysisTask task, AgentAnswer answer) {
        if (answer.getEvidence() == null || answer.getEvidence().isEmpty()) {
            return;
        }
        List<LogFile> files = logFileRepository.findByUpload_IdOrderByCreatedAtAsc(task.getUpload().getId());
        if (files.isEmpty()) {
            return;
        }
        List<LogEvidenceRef> created = new ArrayList<>();
        for (AnswerEvidence ev : answer.getEvidence()) {
            String ref = ev.getReference() == null ? "" : ev.getReference();
            String detail = ev.getDetail() == null ? "" : ev.getDetail();
            Matcher m = LINE_RANGE.matcher(ref + ' ' + detail);
            while (m.find() && created.size() < 20) {
                LogFile target = resolveFile(files, m.group(1));
                if (target == null) {
                    continue;
                }
                int start = parseInt(m.group(2), 1);
                int end = parseInt(m.group(3), start);
                if (end < start) {
                    end = start;
                }
                LogEvidenceRef refEntity = new LogEvidenceRef();
                refEntity.setTask(task);
                refEntity.setLogFile(target);
                refEntity.setLineStart(start);
                refEntity.setLineEnd(end);
                refEntity.setSnippet(truncate(detail, 1000));
                refEntity.setMaskedSnippet(maskingService.mask(truncate(detail, 1000)));
                refEntity.setEvidenceType(ev.getTitle());
                created.add(refEntity);
            }
        }
        if (!created.isEmpty()) {
            evidenceRepository.saveAll(created);
            log.info("写入日志证据 {} 条 taskId={}", created.size(), task.getId());
        }
    }

    private LogFile resolveFile(List<LogFile> files, String fileIdGroup) {
        if (fileIdGroup == null || fileIdGroup.isBlank()) {
            return files.get(0);
        }
        try {
            long id = Long.parseLong(fileIdGroup);
            return files.stream().filter(f -> f.getId() == id).findFirst().orElse(files.get(0));
        } catch (NumberFormatException ex) {
            return files.get(0);
        }
    }

    private int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private Object nullSafe(String value) {
        return value == null ? "" : value;
    }
}
