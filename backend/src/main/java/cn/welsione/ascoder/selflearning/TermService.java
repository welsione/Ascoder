package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 自学习术语管理服务，维护项目空间术语字典。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TermService {

    private final ProjectSpaceService projectSpaceService;
    private final LearningTermJpaRepository termRepository;

    @Transactional(readOnly = true)
    public List<LearningTermResponse> listTerms(Long projectSpaceId) {
        projectSpaceService.getEntity(projectSpaceId);
        return termRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).stream()
                .map(LearningTermResponse::from)
                .toList();
    }

    @Transactional
    public LearningTermResponse createTerm(Long projectSpaceId, SaveLearningTermRequest request) {
        String term = request.getTerm().trim();
        if (termRepository.existsByProjectSpace_IdAndTerm(projectSpaceId, term)) {
            throw new DuplicateException("术语已存在");
        }
        LearningTerm learningTerm = new LearningTerm();
        learningTerm.setProjectSpace(projectSpaceService.getEntity(projectSpaceId));
        applyTermRequest(learningTerm, request);
        return LearningTermResponse.from(termRepository.save(learningTerm));
    }

    @Transactional
    public LearningTermResponse updateTerm(Long projectSpaceId, Long termId, SaveLearningTermRequest request) {
        LearningTerm term = term(projectSpaceId, termId);
        applyTermRequest(term, request);
        term.touch();
        return LearningTermResponse.from(termRepository.save(term));
    }

    @Transactional
    public void deleteTerm(Long projectSpaceId, Long termId) {
        termRepository.delete(term(projectSpaceId, termId));
    }

    private LearningTerm term(Long projectSpaceId, Long termId) {
        return termRepository.findByIdAndProjectSpace_Id(termId, projectSpaceId)
                .orElseThrow(() -> new ResourceNotFoundException("自学习术语", termId));
    }

    private void applyTermRequest(LearningTerm term, SaveLearningTermRequest request) {
        term.setTerm(request.getTerm().trim());
        term.setAliasesJson(SelfLearningTextUtil.trimToNull(request.getAliasesJson()));
        term.setDefinition(request.getDefinition().trim());
        term.setScope(SelfLearningTextUtil.trimToNull(request.getScope()));
        term.setExamples(SelfLearningTextUtil.trimToNull(request.getExamples()));
        term.setSource(SelfLearningTextUtil.trimToNull(request.getSource()) == null ? "manual" : request.getSource().trim());
        term.setConfidence(request.getConfidence() == null ? term.getConfidence() : request.getConfidence());
    }
}
