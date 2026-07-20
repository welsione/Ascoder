package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.question.api.CreateQuestionRequest;
import cn.welsione.ascoder.question.domain.Conversation;
import cn.welsione.ascoder.question.persistence.ConversationJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 会话解析与创建，从请求中解析已有会话或创建新会话。
 */
@Component
@RequiredArgsConstructor
class ConversationResolver {

    private final ConversationJpaRepository conversationRepository;

    Conversation resolve(CreateQuestionRequest request, ProjectSpace projectSpace, ProjectSpaceMember primary) {
        if (request.getConversationId() != null) {
            Conversation existing = conversationRepository.findById(request.getConversationId())
                    .orElseThrow(() -> new ResourceNotFoundException("会话", request.getConversationId()));
            if (existing.getProjectSpaceId() == null || !existing.getProjectSpaceId().equals(projectSpace.getId())) {
                throw new ValidationException("会话不属于当前项目空间");
            }
            return existing;
        }

        Conversation conversation = new Conversation();
        conversation.setProjectSpaceId(projectSpace.getId());
        conversation.setRepositoryId(primary.getRepository() == null ? null : primary.getRepository().getId());
        conversation.setBranchWorkspaceId(primary.getBranchWorkspace() == null ? null : primary.getBranchWorkspace().getId());
        conversation.setTitle(titleFrom(request.getText()));
        conversation.setRole(request.getRole());
        conversation.setBranchName(primary.getBranchName());
        conversation.setCommitSha(primary.getCommitSha());
        return conversationRepository.save(conversation);
    }

    private String titleFrom(String question) {
        String normalized = question.trim().replaceAll("\\s+", " ");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }
}
