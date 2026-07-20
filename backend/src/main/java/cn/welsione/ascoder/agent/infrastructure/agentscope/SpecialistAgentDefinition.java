package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.SpecialistTaskKind;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Specialist Agent 注册定义，描述 Agent 身份、提示词、触发条件和前端展示文案。
 */
@Value
@Builder
class SpecialistAgentDefinition {
    String agentId;
    String displayName;
    String description;
    String systemPrompt;
    SpecialistTaskKind taskKind;
    int maxIters;
    boolean required;
    List<String> roleKeys;
    List<String> questionKeywords;
    String handoffTitle;
    String handoffDescription;
    String returnTitle;
    String returnDescription;

    boolean supports(AgentRequestView request) {
        if (required) {
            return true;
        }
        String normalizedRole = normalize(request.getRole());
        if (roleKeys != null && roleKeys.contains(normalizedRole)) {
            return true;
        }
        String question = request.getQuestion() == null ? "" : request.getQuestion();
        if (questionKeywords == null || questionKeywords.isEmpty()) {
            return false;
        }
        return questionKeywords.stream().anyMatch(question::contains);
    }

    private String normalize(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        return role.trim().toLowerCase().replace("-", "_").replace(" ", "_");
    }

    /**
     * 供触发条件读取的最小请求视图，避免定义类直接依赖完整 AgentRequest。
     */
    @Value
    static class AgentRequestView {
        String question;
        String role;
    }
}
