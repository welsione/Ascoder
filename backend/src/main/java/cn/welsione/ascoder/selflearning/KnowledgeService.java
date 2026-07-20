package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 正式知识管理服务，维护项目空间内的正式知识条目。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final ProjectSpaceService projectSpaceService;
    private final SelfLearningEntityLoader entityLoader;
    private final LearningKnowledgeItemJpaRepository knowledgeRepository;

    @Transactional(readOnly = true)
    public List<LearningKnowledgeItemResponse> listKnowledgeItems(Long projectSpaceId, LearningKnowledgeStatus status) {
        projectSpaceService.getEntity(projectSpaceId);
        return knowledgeRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).stream()
                .filter(item -> status == null || item.getStatus() == status)
                .map(LearningKnowledgeItemResponse::from)
                .toList();
    }

    @Transactional
    public LearningKnowledgeItemResponse createKnowledgeItem(Long projectSpaceId, SaveLearningKnowledgeItemRequest request) {
        LearningKnowledgeItem item = new LearningKnowledgeItem();
        item.setProjectSpace(projectSpaceService.getEntity(projectSpaceId));
        applyKnowledgeRequest(item, request);
        return LearningKnowledgeItemResponse.from(knowledgeRepository.save(item));
    }

    @Transactional
    public LearningKnowledgeItemResponse updateKnowledgeItem(Long projectSpaceId, Long itemId, SaveLearningKnowledgeItemRequest request) {
        LearningKnowledgeItem item = knowledgeItem(projectSpaceId, itemId);
        applyKnowledgeRequest(item, request);
        item.touch();
        return LearningKnowledgeItemResponse.from(knowledgeRepository.save(item));
    }

    @Transactional
    public void deleteKnowledgeItem(Long projectSpaceId, Long itemId) {
        knowledgeRepository.delete(knowledgeItem(projectSpaceId, itemId));
    }

    @Transactional
    public LearningKnowledgeItemResponse archiveKnowledgeItem(Long projectSpaceId, Long itemId) {
        LearningKnowledgeItem item = knowledgeItem(projectSpaceId, itemId);
        item.archive();
        return LearningKnowledgeItemResponse.from(knowledgeRepository.save(item));
    }

    @Transactional
    public LearningKnowledgeItemResponse markKnowledgeItemStale(Long projectSpaceId, Long itemId, ReviewLearningInsightRequest request) {
        LearningKnowledgeItem item = knowledgeItem(projectSpaceId, itemId);
        item.markStale(request == null ? null : SelfLearningTextUtil.trimToNull(request.getReviewComment()));
        return LearningKnowledgeItemResponse.from(knowledgeRepository.save(item));
    }

    private LearningKnowledgeItem knowledgeItem(Long projectSpaceId, Long itemId) {
        return knowledgeRepository.findByIdAndProjectSpace_Id(itemId, projectSpaceId)
                .orElseThrow(() -> new ResourceNotFoundException("正式知识", itemId));
    }

    private void applyKnowledgeRequest(LearningKnowledgeItem item, SaveLearningKnowledgeItemRequest request) {
        item.setRepository(entityLoader.repository(request.getRepositoryId()));
        item.setSourceInsightIdsJson(SelfLearningTextUtil.trimToNull(request.getSourceInsightIdsJson()));
        item.setSourceRawEventIdsJson(SelfLearningTextUtil.trimToNull(request.getSourceRawEventIdsJson()));
        item.setType(request.getType() == null ? LearningKnowledgeType.QUESTION_ANSWER : request.getType());
        item.setStatus(request.getStatus() == null ? LearningKnowledgeStatus.VERIFIED : request.getStatus());
        item.setTitle(request.getTitle().trim());
        item.setContent(request.getContent().trim());
        item.setSummary(SelfLearningTextUtil.trimToNull(request.getSummary()));
        item.setApplicableScope(SelfLearningTextUtil.trimToNull(request.getApplicableScope()));
        item.setEvidenceJson(SelfLearningTextUtil.trimToNull(request.getEvidenceJson()));
        item.setGitProvenanceJson(SelfLearningTextUtil.trimToNull(request.getGitProvenanceJson()));
        item.setTags(SelfLearningTextUtil.trimToNull(request.getTags()));
        item.setConfidence(request.getConfidence() == null ? item.getConfidence() : request.getConfidence());
    }
}
