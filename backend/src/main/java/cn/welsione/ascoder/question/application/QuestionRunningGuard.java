package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.question.domain.QuestionStatus;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查询项目空间内运行中问题，用于保护会破坏回答上下文的空间写操作。
 */
@Service
@RequiredArgsConstructor
public class QuestionRunningGuard {

    private final QuestionJpaRepository repository;

    @Transactional(readOnly = true)
    public boolean hasRunningQuestion(Long projectSpaceId) {
        return repository.existsByProjectSpaceIdAndStatus(projectSpaceId, QuestionStatus.RUNNING);
    }
}
