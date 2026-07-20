package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.question.application.QuestionQueryPort;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.RepositoryQueryPort;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自学习模块共享的实体加载与持久化网关，收口各 Repository 的查询、保存与统计，
 * 使拆分后的 Service 不再各自注入 Repository，从而收敛依赖数。
 * 作为基础设施组件允许依赖较多 Repository，但方法职责清晰、命名直观。
 */
@Component
@RequiredArgsConstructor
public class SelfLearningEntityLoader {

    private final ProjectSpaceService projectSpaceService;
    private final SelfLearningSettingsJpaRepository settingsRepository;
    private final RepositoryQueryPort repositoryQueryPort;
    private final QuestionQueryPort questionQueryPort;
    private final LearningInsightJpaRepository insightRepository;
    private final LearningKnowledgeItemJpaRepository knowledgeRepository;
    private final LearningRawEventJpaRepository rawEventRepository;
    private final LearningAgentRunJpaRepository agentRunRepository;
    private final LearningExperienceJpaRepository experienceRepository;
    private final LearningTermJpaRepository termRepository;
    private final LearningCorrectionJpaRepository correctionRepository;

    // ---------- 设置 ----------

    public SelfLearningSettings settings(Long projectSpaceId) {
        return settingsRepository.findByProjectSpace_Id(projectSpaceId)
                .orElseGet(() -> createDefaultSettings(projectSpaceId));
    }

    public SelfLearningSettings saveSettings(SelfLearningSettings settings) {
        return settingsRepository.save(settings);
    }

    // ---------- 项目空间 / 仓库 / 问题 ----------

    public ProjectSpace projectSpace(Long projectSpaceId) {
        return projectSpaceService.getEntity(projectSpaceId);
    }

    public CodeRepository repository(Long repositoryId) {
        if (repositoryId == null) {
            return null;
        }
        return repositoryQueryPort.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("仓库", repositoryId));
    }

    public CodeRepository findRepository(Long repositoryId) {
        if (repositoryId == null) {
            return null;
        }
        return repositoryQueryPort.findById(repositoryId).orElse(null);
    }

    public Question question(Long questionId) {
        if (questionId == null) {
            return null;
        }
        return questionQueryPort.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("问题", questionId));
    }

    public Question findQuestion(Long questionId) {
        if (questionId == null) {
            return null;
        }
        return questionQueryPort.findById(questionId).orElse(null);
    }

    public List<Question> questionsInProjectSpace(Long projectSpaceId) {
        return questionQueryPort.findByProjectSpaceId(projectSpaceId);
    }

    public List<QueryPlan> queryPlansByQuestionIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return questionQueryPort.findByQuestionIdIn(questionIds);
    }

    // ---------- 候选洞察 ----------

    public List<LearningInsight> insightsByProjectSpace(Long projectSpaceId) {
        return insightRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId);
    }

    public LearningInsight insight(Long projectSpaceId, Long insightId) {
        return insightRepository.findByIdAndProjectSpace_Id(insightId, projectSpaceId)
                .orElseThrow(() -> new ResourceNotFoundException("候选洞察", insightId));
    }

    public LearningInsight saveInsight(LearningInsight insight) {
        return insightRepository.save(insight);
    }

    public void deleteInsights(List<LearningInsight> insights) {
        insightRepository.deleteAll(insights);
    }

    // ---------- 正式知识 ----------

    public List<LearningKnowledgeItem> knowledgeItemsByProjectSpace(Long projectSpaceId) {
        return knowledgeRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId);
    }

    public LearningKnowledgeItem saveKnowledgeItem(LearningKnowledgeItem item) {
        return knowledgeRepository.save(item);
    }

    public List<LearningKnowledgeItem> saveKnowledgeItems(List<LearningKnowledgeItem> items) {
        return knowledgeRepository.saveAll(items);
    }

    // ---------- 原始事件 ----------

    public List<LearningRawEvent> rawEventsByProjectSpace(Long projectSpaceId) {
        return rawEventRepository.findByProjectSpace_IdOrderByCreatedAtDesc(projectSpaceId);
    }

    public List<LearningRawEvent> recentRawEvents(Long projectSpaceId, int limit) {
        return rawEventRepository.findTop50ByProjectSpace_IdOrderByCreatedAtDesc(projectSpaceId);
    }

    public List<LearningRawEvent> rawEventsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return rawEventRepository.findAllById(ids);
    }

    public LearningRawEvent saveRawEvent(LearningRawEvent event) {
        return rawEventRepository.save(event);
    }

    public void deleteRawEvents(List<LearningRawEvent> events) {
        rawEventRepository.deleteAll(events);
    }

    // ---------- Agent 运行记录 ----------

    public List<LearningAgentRun> agentRunsByProjectSpace(Long projectSpaceId) {
        return agentRunRepository.findTop20ByProjectSpace_IdOrderByCreatedAtDesc(projectSpaceId);
    }

    public LearningAgentRun agentRun(Long runId) {
        return agentRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("自学习运行记录", runId));
    }

    public LearningAgentRun saveAgentRun(LearningAgentRun run) {
        return agentRunRepository.save(run);
    }

    // ---------- 经验 / 术语 / 纠正 ----------

    public List<LearningExperience> experiencesByProjectSpace(Long projectSpaceId) {
        return experienceRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId);
    }

    public List<LearningTerm> termsByProjectSpace(Long projectSpaceId) {
        return termRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId);
    }

    public List<LearningCorrection> correctionsByProjectSpace(Long projectSpaceId) {
        return correctionRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId);
    }

    // ---------- 统计（供摘要） ----------

    public int countRawEvents(Long projectSpaceId) {
        return rawEventRepository.findByProjectSpace_IdOrderByCreatedAtDesc(projectSpaceId).size();
    }

    public int countInsights(Long projectSpaceId) {
        return insightRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).size();
    }

    public long countPendingInsights(Long projectSpaceId) {
        return insightRepository.countByProjectSpace_IdAndStatus(projectSpaceId, LearningInsightStatus.PENDING_REVIEW);
    }

    public int countKnowledgeItems(Long projectSpaceId) {
        return knowledgeRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).size();
    }

    public int countExperiences(Long projectSpaceId) {
        return experienceRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).size();
    }

    public long countCandidateExperiences(Long projectSpaceId) {
        return experienceRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).stream()
                .filter(item -> item.getStatus() == LearningExperienceStatus.CANDIDATE)
                .count();
    }

    public int countCorrections(Long projectSpaceId) {
        return correctionRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).size();
    }

    public int countTerms(Long projectSpaceId) {
        return termRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).size();
    }

    private SelfLearningSettings createDefaultSettings(Long projectSpaceId) {
        ProjectSpace space = projectSpaceService.getEntity(projectSpaceId);
        SelfLearningSettings settings = new SelfLearningSettings();
        settings.setProjectSpace(space);
        return settingsRepository.save(settings);
    }
}
