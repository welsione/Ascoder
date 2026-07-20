package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 自学习经验管理服务，维护兼容历史经验数据的增删改查与状态流转。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceService {

    private final ProjectSpaceService projectSpaceService;
    private final SelfLearningEntityLoader entityLoader;
    private final LearningExperienceJpaRepository experienceRepository;

    @Transactional(readOnly = true)
    public List<LearningExperienceResponse> listExperiences(
            Long projectSpaceId,
            LearningExperienceType type,
            LearningExperienceStatus status
    ) {
        projectSpaceService.getEntity(projectSpaceId);
        return experienceRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).stream()
                .filter(item -> type == null || item.getType() == type)
                .filter(item -> status == null || item.getStatus() == status)
                .map(LearningExperienceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LearningExperienceResponse getExperience(Long projectSpaceId, Long experienceId) {
        return LearningExperienceResponse.from(experience(projectSpaceId, experienceId));
    }

    @Transactional
    public LearningExperienceResponse createExperience(Long projectSpaceId, SaveLearningExperienceRequest request) {
        LearningExperience experience = new LearningExperience();
        experience.setProjectSpace(projectSpaceService.getEntity(projectSpaceId));
        applyExperienceRequest(experience, request);
        log.info("创建自学习经验，projectSpaceId={}，title={}", projectSpaceId, request.getTitle());
        return LearningExperienceResponse.from(experienceRepository.save(experience));
    }

    @Transactional
    public LearningExperienceResponse updateExperience(Long projectSpaceId, Long experienceId, SaveLearningExperienceRequest request) {
        LearningExperience experience = experience(projectSpaceId, experienceId);
        applyExperienceRequest(experience, request);
        experience.touch();
        return LearningExperienceResponse.from(experienceRepository.save(experience));
    }

    @Transactional
    public void deleteExperience(Long projectSpaceId, Long experienceId) {
        experienceRepository.delete(experience(projectSpaceId, experienceId));
    }

    @Transactional
    public LearningExperienceResponse verifyExperience(Long projectSpaceId, Long experienceId) {
        LearningExperience experience = experience(projectSpaceId, experienceId);
        experience.verify();
        return LearningExperienceResponse.from(experienceRepository.save(experience));
    }

    @Transactional
    public LearningExperienceResponse rejectExperience(Long projectSpaceId, Long experienceId) {
        LearningExperience experience = experience(projectSpaceId, experienceId);
        experience.reject();
        return LearningExperienceResponse.from(experienceRepository.save(experience));
    }

    @Transactional
    public LearningExperienceResponse archiveExperience(Long projectSpaceId, Long experienceId) {
        LearningExperience experience = experience(projectSpaceId, experienceId);
        experience.archive();
        return LearningExperienceResponse.from(experienceRepository.save(experience));
    }

    private LearningExperience experience(Long projectSpaceId, Long experienceId) {
        return experienceRepository.findByIdAndProjectSpace_Id(experienceId, projectSpaceId)
                .orElseThrow(() -> new ResourceNotFoundException("自学习经验", experienceId));
    }

    private void applyExperienceRequest(LearningExperience experience, SaveLearningExperienceRequest request) {
        experience.setRepository(entityLoader.repository(request.getRepositoryId()));
        experience.setSourceQuestion(entityLoader.question(request.getSourceQuestionId()));
        experience.setType(request.getType() == null ? LearningExperienceType.QUESTION_ANSWER : request.getType());
        experience.setStatus(request.getStatus() == null ? LearningExperienceStatus.CANDIDATE : request.getStatus());
        experience.setTitle(request.getTitle().trim());
        experience.setProblem(SelfLearningTextUtil.trimToNull(request.getProblem()));
        experience.setConclusion(request.getConclusion().trim());
        experience.setApplicableScope(SelfLearningTextUtil.trimToNull(request.getApplicableScope()));
        experience.setEvidenceJson(SelfLearningTextUtil.trimToNull(request.getEvidenceJson()));
        experience.setGitProvenanceJson(SelfLearningTextUtil.trimToNull(request.getGitProvenanceJson()));
        experience.setTags(SelfLearningTextUtil.trimToNull(request.getTags()));
        experience.setConfidence(request.getConfidence() == null ? experience.getConfidence() : request.getConfidence());
    }
}
