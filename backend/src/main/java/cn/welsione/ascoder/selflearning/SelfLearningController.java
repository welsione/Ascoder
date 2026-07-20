package cn.welsione.ascoder.selflearning;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自学习 REST 控制器，提供项目空间级原始记录、候选洞察、正式知识和兼容经验管理。
 */
@RestController
@RequestMapping("/api/project-spaces/{projectSpaceId}/self-learning")
@RequiredArgsConstructor
public class SelfLearningController {

    private final SelfLearningService service;
    private final InsightService insightService;
    private final KnowledgeService knowledgeService;
    private final ExperienceService experienceService;
    private final TermService termService;
    private final CorrectionService correctionService;
    private final AgentRunService agentRunService;
    private final SelfLearningAgentRunScheduler agentRunScheduler;

    @GetMapping("/summary")
    public SelfLearningSummaryResponse summary(@PathVariable Long projectSpaceId) {
        return service.summary(projectSpaceId);
    }

    @GetMapping("/settings")
    public SelfLearningSettingsResponse settings(@PathVariable Long projectSpaceId) {
        return service.getSettings(projectSpaceId);
    }

    @PatchMapping("/settings")
    public SelfLearningSettingsResponse updateSettings(
            @PathVariable Long projectSpaceId,
            @RequestBody UpdateSelfLearningSettingsRequest request
    ) {
        return service.updateSettings(projectSpaceId, request);
    }

    @GetMapping("/raw-events")
    public List<LearningRawEventResponse> rawEvents(@PathVariable Long projectSpaceId) {
        return agentRunService.listRawEvents(projectSpaceId);
    }

    @PostMapping("/raw-events/import-history")
    public ImportHistoryRawEventsResponse importHistoryRawEvents(@PathVariable Long projectSpaceId) {
        return agentRunService.importHistoryRawEvents(projectSpaceId);
    }

    @PostMapping("/raw-events/cleanup-legacy")
    public CleanupLegacyRawEventsResponse cleanupLegacyRawEvents(@PathVariable Long projectSpaceId) {
        return agentRunService.cleanupLegacyRawEvents(projectSpaceId);
    }

    @PostMapping("/insights/cleanup-legacy")
    public CleanupLegacyInsightsResponse cleanupLegacyInsights(@PathVariable Long projectSpaceId) {
        return insightService.cleanupLegacyInsights(projectSpaceId);
    }

    @GetMapping("/insights")
    public List<LearningInsightResponse> insights(
            @PathVariable Long projectSpaceId,
            @RequestParam(required = false) LearningInsightStatus status
    ) {
        return insightService.listInsights(projectSpaceId, status);
    }

    @PostMapping("/insights")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningInsightResponse createInsight(
            @PathVariable Long projectSpaceId,
            @Valid @RequestBody SaveLearningInsightRequest request
    ) {
        return insightService.createInsight(projectSpaceId, request);
    }

    @PatchMapping("/insights/{insightId}")
    public LearningInsightResponse updateInsight(
            @PathVariable Long projectSpaceId,
            @PathVariable Long insightId,
            @Valid @RequestBody SaveLearningInsightRequest request
    ) {
        return insightService.updateInsight(projectSpaceId, insightId, request);
    }

    @PostMapping("/insights/{insightId}/approve")
    public LearningInsightResponse approveInsight(
            @PathVariable Long projectSpaceId,
            @PathVariable Long insightId,
            @RequestBody(required = false) ReviewLearningInsightRequest request
    ) {
        return insightService.approveInsight(projectSpaceId, insightId, request);
    }

    @PostMapping("/insights/{insightId}/reject")
    public LearningInsightResponse rejectInsight(
            @PathVariable Long projectSpaceId,
            @PathVariable Long insightId,
            @RequestBody(required = false) ReviewLearningInsightRequest request
    ) {
        return insightService.rejectInsight(projectSpaceId, insightId, request);
    }

    @PostMapping("/insights/{insightId}/verify")
    public LearningInsightVerificationResponse verifyInsight(
            @PathVariable Long projectSpaceId,
            @PathVariable Long insightId
    ) {
        return insightService.verifyInsight(projectSpaceId, insightId);
    }

    @PostMapping("/insights/{insightId}/refine")
    public RefineLearningInsightResponse refineInsight(
            @PathVariable Long projectSpaceId,
            @PathVariable Long insightId,
            @Valid @RequestBody RefineLearningInsightRequest request
    ) {
        return insightService.refineInsight(projectSpaceId, insightId, request);
    }

    @GetMapping("/knowledge-items")
    public List<LearningKnowledgeItemResponse> knowledgeItems(
            @PathVariable Long projectSpaceId,
            @RequestParam(required = false) LearningKnowledgeStatus status
    ) {
        return knowledgeService.listKnowledgeItems(projectSpaceId, status);
    }

    @PostMapping("/knowledge-items")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningKnowledgeItemResponse createKnowledgeItem(
            @PathVariable Long projectSpaceId,
            @Valid @RequestBody SaveLearningKnowledgeItemRequest request
    ) {
        return knowledgeService.createKnowledgeItem(projectSpaceId, request);
    }

    @PatchMapping("/knowledge-items/{knowledgeItemId}")
    public LearningKnowledgeItemResponse updateKnowledgeItem(
            @PathVariable Long projectSpaceId,
            @PathVariable Long knowledgeItemId,
            @Valid @RequestBody SaveLearningKnowledgeItemRequest request
    ) {
        return knowledgeService.updateKnowledgeItem(projectSpaceId, knowledgeItemId, request);
    }

    @DeleteMapping("/knowledge-items/{knowledgeItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKnowledgeItem(
            @PathVariable Long projectSpaceId,
            @PathVariable Long knowledgeItemId
    ) {
        knowledgeService.deleteKnowledgeItem(projectSpaceId, knowledgeItemId);
    }

    @PostMapping("/knowledge-items/{knowledgeItemId}/archive")
    public LearningKnowledgeItemResponse archiveKnowledgeItem(
            @PathVariable Long projectSpaceId,
            @PathVariable Long knowledgeItemId
    ) {
        return knowledgeService.archiveKnowledgeItem(projectSpaceId, knowledgeItemId);
    }

    @PostMapping("/knowledge-items/{knowledgeItemId}/mark-stale")
    public LearningKnowledgeItemResponse markKnowledgeItemStale(
            @PathVariable Long projectSpaceId,
            @PathVariable Long knowledgeItemId,
            @RequestBody(required = false) ReviewLearningInsightRequest request
    ) {
        return knowledgeService.markKnowledgeItemStale(projectSpaceId, knowledgeItemId, request);
    }

    @PostMapping("/agent-runs")
    public SelfLearningAgentRunResponse runSelfLearningAgent(
            @PathVariable Long projectSpaceId,
            @RequestParam(required = false) Integer limit
    ) {
        return agentRunScheduler.submit(projectSpaceId, limit);
    }

    @GetMapping("/agent-runs")
    public List<LearningAgentRunResponse> agentRuns(@PathVariable Long projectSpaceId) {
        return agentRunService.listAgentRuns(projectSpaceId);
    }

    @GetMapping("/experiences")
    public List<LearningExperienceResponse> experiences(
            @PathVariable Long projectSpaceId,
            @RequestParam(required = false) LearningExperienceType type,
            @RequestParam(required = false) LearningExperienceStatus status
    ) {
        return experienceService.listExperiences(projectSpaceId, type, status);
    }

    @GetMapping("/experiences/{experienceId}")
    public LearningExperienceResponse experience(
            @PathVariable Long projectSpaceId,
            @PathVariable Long experienceId
    ) {
        return experienceService.getExperience(projectSpaceId, experienceId);
    }

    @PostMapping("/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningExperienceResponse createExperience(
            @PathVariable Long projectSpaceId,
            @Valid @RequestBody SaveLearningExperienceRequest request
    ) {
        return experienceService.createExperience(projectSpaceId, request);
    }

    @PatchMapping("/experiences/{experienceId}")
    public LearningExperienceResponse updateExperience(
            @PathVariable Long projectSpaceId,
            @PathVariable Long experienceId,
            @Valid @RequestBody SaveLearningExperienceRequest request
    ) {
        return experienceService.updateExperience(projectSpaceId, experienceId, request);
    }

    @DeleteMapping("/experiences/{experienceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExperience(
            @PathVariable Long projectSpaceId,
            @PathVariable Long experienceId
    ) {
        experienceService.deleteExperience(projectSpaceId, experienceId);
    }

    @PostMapping("/experiences/{experienceId}/verify")
    public LearningExperienceResponse verifyExperience(
            @PathVariable Long projectSpaceId,
            @PathVariable Long experienceId
    ) {
        return experienceService.verifyExperience(projectSpaceId, experienceId);
    }

    @PostMapping("/experiences/{experienceId}/reject")
    public LearningExperienceResponse rejectExperience(
            @PathVariable Long projectSpaceId,
            @PathVariable Long experienceId
    ) {
        return experienceService.rejectExperience(projectSpaceId, experienceId);
    }

    @PostMapping("/experiences/{experienceId}/archive")
    public LearningExperienceResponse archiveExperience(
            @PathVariable Long projectSpaceId,
            @PathVariable Long experienceId
    ) {
        return experienceService.archiveExperience(projectSpaceId, experienceId);
    }

    @GetMapping("/terms")
    public List<LearningTermResponse> terms(@PathVariable Long projectSpaceId) {
        return termService.listTerms(projectSpaceId);
    }

    @PostMapping("/terms")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningTermResponse createTerm(
            @PathVariable Long projectSpaceId,
            @Valid @RequestBody SaveLearningTermRequest request
    ) {
        return termService.createTerm(projectSpaceId, request);
    }

    @PatchMapping("/terms/{termId}")
    public LearningTermResponse updateTerm(
            @PathVariable Long projectSpaceId,
            @PathVariable Long termId,
            @Valid @RequestBody SaveLearningTermRequest request
    ) {
        return termService.updateTerm(projectSpaceId, termId, request);
    }

    @DeleteMapping("/terms/{termId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTerm(
            @PathVariable Long projectSpaceId,
            @PathVariable Long termId
    ) {
        termService.deleteTerm(projectSpaceId, termId);
    }

    @GetMapping("/corrections")
    public List<LearningCorrectionResponse> corrections(
            @PathVariable Long projectSpaceId,
            @RequestParam(required = false) LearningCorrectionStatus status
    ) {
        return correctionService.listCorrections(projectSpaceId, status);
    }

    @PostMapping("/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningCorrectionResponse createCorrection(
            @PathVariable Long projectSpaceId,
            @Valid @RequestBody SaveLearningCorrectionRequest request
    ) {
        return correctionService.createCorrection(projectSpaceId, request);
    }

    @PatchMapping("/corrections/{correctionId}")
    public LearningCorrectionResponse updateCorrection(
            @PathVariable Long projectSpaceId,
            @PathVariable Long correctionId,
            @Valid @RequestBody SaveLearningCorrectionRequest request
    ) {
        return correctionService.updateCorrection(projectSpaceId, correctionId, request);
    }

    @DeleteMapping("/corrections/{correctionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCorrection(
            @PathVariable Long projectSpaceId,
            @PathVariable Long correctionId
    ) {
        correctionService.deleteCorrection(projectSpaceId, correctionId);
    }

    @PostMapping("/corrections/{correctionId}/verify")
    public LearningCorrectionResponse verifyCorrection(
            @PathVariable Long projectSpaceId,
            @PathVariable Long correctionId
    ) {
        return correctionService.verifyCorrection(projectSpaceId, correctionId);
    }

    @PostMapping("/corrections/{correctionId}/reject")
    public LearningCorrectionResponse rejectCorrection(
            @PathVariable Long projectSpaceId,
            @PathVariable Long correctionId
    ) {
        return correctionService.rejectCorrection(projectSpaceId, correctionId);
    }
}
