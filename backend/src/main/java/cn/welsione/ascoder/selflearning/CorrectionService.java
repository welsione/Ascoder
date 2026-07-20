package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 自学习纠错管理服务，维护用户反馈的历史纠错记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectionService {

    private final ProjectSpaceService projectSpaceService;
    private final SelfLearningEntityLoader entityLoader;
    private final LearningCorrectionJpaRepository correctionRepository;

    @Transactional(readOnly = true)
    public List<LearningCorrectionResponse> listCorrections(Long projectSpaceId, LearningCorrectionStatus status) {
        projectSpaceService.getEntity(projectSpaceId);
        return correctionRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).stream()
                .filter(item -> status == null || item.getStatus() == status)
                .map(LearningCorrectionResponse::from)
                .toList();
    }

    @Transactional
    public LearningCorrectionResponse createCorrection(Long projectSpaceId, SaveLearningCorrectionRequest request) {
        LearningCorrection correction = new LearningCorrection();
        correction.setProjectSpace(projectSpaceService.getEntity(projectSpaceId));
        applyCorrectionRequest(correction, request);
        return LearningCorrectionResponse.from(correctionRepository.save(correction));
    }

    @Transactional
    public LearningCorrectionResponse updateCorrection(Long projectSpaceId, Long correctionId, SaveLearningCorrectionRequest request) {
        LearningCorrection correction = correction(projectSpaceId, correctionId);
        applyCorrectionRequest(correction, request);
        correction.touch();
        return LearningCorrectionResponse.from(correctionRepository.save(correction));
    }

    @Transactional
    public void deleteCorrection(Long projectSpaceId, Long correctionId) {
        correctionRepository.delete(correction(projectSpaceId, correctionId));
    }

    @Transactional
    public LearningCorrectionResponse verifyCorrection(Long projectSpaceId, Long correctionId) {
        LearningCorrection correction = correction(projectSpaceId, correctionId);
        correction.verify();
        return LearningCorrectionResponse.from(correctionRepository.save(correction));
    }

    @Transactional
    public LearningCorrectionResponse rejectCorrection(Long projectSpaceId, Long correctionId) {
        LearningCorrection correction = correction(projectSpaceId, correctionId);
        correction.reject();
        return LearningCorrectionResponse.from(correctionRepository.save(correction));
    }

    private LearningCorrection correction(Long projectSpaceId, Long correctionId) {
        return correctionRepository.findByIdAndProjectSpace_Id(correctionId, projectSpaceId)
                .orElseThrow(() -> new ResourceNotFoundException("自学习纠错", correctionId));
    }

    private void applyCorrectionRequest(LearningCorrection correction, SaveLearningCorrectionRequest request) {
        correction.setSourceQuestion(entityLoader.question(request.getSourceQuestionId()));
        correction.setWrongConclusion(request.getWrongConclusion().trim());
        correction.setCorrectedConclusion(request.getCorrectedConclusion().trim());
        correction.setVerificationProcess(SelfLearningTextUtil.trimToNull(request.getVerificationProcess()));
        correction.setEvidenceJson(SelfLearningTextUtil.trimToNull(request.getEvidenceJson()));
        correction.setStatus(request.getStatus() == null ? LearningCorrectionStatus.CANDIDATE : request.getStatus());
    }
}
