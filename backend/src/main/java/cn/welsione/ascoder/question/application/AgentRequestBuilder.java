package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import cn.welsione.ascoder.question.planning.QuestionPlan;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMember;
import cn.welsione.ascoder.selflearning.SelfLearningContextBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AgentRequest 构建，封装仓库上下文转换和会话历史拼接。
 */
@Component
@RequiredArgsConstructor
class AgentRequestBuilder {

    private final QuestionJpaRepository repository;
    private final SelfLearningContextBuilder selfLearningService;

    @Value("${ascoder.project-space-root:./data/project-spaces}")
    private String projectSpaceRoot;

    @Value("${ascoder.repo-root:./data/repos}")
    private String repoRoot;

    AgentRequest build(Long questionId, ProjectSpace projectSpace, Long conversationId, List<ProjectSpaceMember> members,
                       String question, String role, String queryPlanText, QuestionPlan questionPlan) {
        return build(questionId, projectSpace, conversationId, members, question, role, queryPlanText, questionPlan, null);
    }

    AgentRequest build(Long questionId, ProjectSpace projectSpace, Long conversationId, List<ProjectSpaceMember> members,
                       String question, String role, String queryPlanText, QuestionPlan questionPlan,
                       List<Long> logUploadIds) {
        return new AgentRequest(
                projectSpace.getId(),
                questionId,
                conversationId,
                projectSpace.getName(),
                projectSpace.resolveRootPath(projectSpaceRoot),
                projectSpace.resolveCodegraphIndexPath(projectSpaceRoot),
                toRepositoryContexts(members),
                question,
                role,
                queryPlanText,
                buildConversationContext(conversationId),
                null,
                questionPlan,
                logUploadIds,
                selfLearningService.buildContext(projectSpace.getId(), question)
        );
    }

    private List<AgentRequest.RepositoryContext> toRepositoryContexts(List<ProjectSpaceMember> members) {
        List<AgentRequest.RepositoryContext> contexts = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            contexts.add(toRepositoryContext(members.get(i), i == 0));
        }
        return contexts;
    }

    private AgentRequest.RepositoryContext toRepositoryContext(ProjectSpaceMember member, boolean primary) {
        return new AgentRequest.RepositoryContext(
                member.getRepositoryId(),
                member.getRepositoryName(),
                member.getBranchWorkspaceId(),
                member.getBranchName(),
                member.getCommitSha(),
                resolveMemberWorkspacePath(member),
                null,
                member.getRole(),
                primary
        );
    }

    /**
     * 解析成员工作区路径。优先用 linkPath（相对路径），其次回退到 worktreePath。
     */
    private String resolveMemberWorkspacePath(ProjectSpaceMember member) {
        String linkPath = member.resolveLinkPath(projectSpaceRoot);
        if (linkPath != null && !linkPath.isBlank()) {
            return linkPath;
        }
        return member.getWorktreePath();
    }

    private String buildConversationContext(Long conversationId) {
        List<Question> history = new ArrayList<>(repository
                .findTop6ByConversationIdAndStatusOrderByCreatedAtDesc(conversationId, QuestionStatus.SUCCEEDED));
        Collections.reverse(history);
        if (history.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Question item : history) {
            builder.append("用户：").append(item.getText()).append('\n');
            if (item.getAnswerSummary() != null && !item.getAnswerSummary().isBlank()) {
                builder.append("助手摘要：").append(item.getAnswerSummary()).append('\n');
            } else if (item.getAnswer() != null && !item.getAnswer().isBlank()) {
                builder.append("助手：").append(truncate(item.getAnswer(), 1200)).append('\n');
            }
            builder.append('\n');
        }
        Question latest = history.get(history.size() - 1);
        if (latest.getCodegraphContext() != null && !latest.getCodegraphContext().isBlank()) {
            builder.append("上一轮的代码证据（可复用，不需要重复检索）：\n");
            builder.append(truncate(latest.getCodegraphContext(), 3000)).append('\n');
        }
        return builder.toString().trim();
    }

    private String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
