package cn.welsione.ascoder.agent.domain;

import cn.welsione.ascoder.question.planning.QuestionPlan;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Agent 请求，封装问题、仓库信息和查询计划。
 */
@Value
@AllArgsConstructor
public class AgentRequest {
    Long projectSpaceId;
    Long questionId;
    Long conversationId;
    String projectSpaceName;
    String projectSpaceRootPath;
    String projectSpaceCodegraphIndexPath;
    List<RepositoryContext> repositories;
    String text;
    String role;
    String queryPlan;
    String conversationContext;
    String codeContext;
    QuestionPlan questionPlan;
    List<Long> logUploadIds;
    String selfLearningContext;

    public AgentRequest(Long projectSpaceId, Long questionId, Long conversationId, String projectSpaceName,
                        String projectSpaceRootPath, String projectSpaceCodegraphIndexPath,
                        List<RepositoryContext> repositories, String text, String role,
                        String queryPlan, String conversationContext, String codeContext,
                        QuestionPlan questionPlan, List<Long> logUploadIds) {
        this(
                projectSpaceId,
                questionId,
                conversationId,
                projectSpaceName,
                projectSpaceRootPath,
                projectSpaceCodegraphIndexPath,
                repositories,
                text,
                role,
                queryPlan,
                conversationContext,
                codeContext,
                questionPlan,
                logUploadIds,
                null
        );
    }

    public RepositoryContext getPrimaryRepository() {
        if (repositories == null || repositories.isEmpty()) {
            return null;
        }
        return repositories.stream()
                .filter(RepositoryContext::isPrimary)
                .findFirst()
                .orElse(repositories.get(0));
    }

    @Value
    @AllArgsConstructor
    public static class RepositoryContext {
        Long repositoryId;
        String repositoryName;
        Long branchWorkspaceId;
        String branchName;
        String commitSha;
        String workspacePath;
        String codegraphIndexPath;
        String role;
        boolean primary;
    }
}
