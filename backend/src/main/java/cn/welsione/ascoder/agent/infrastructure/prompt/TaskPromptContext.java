package cn.welsione.ascoder.agent.infrastructure.prompt;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 统一任务提示词上下文，覆盖所有 Specialist / Orchestrator 可能用到的变量。
 *
 * <p>模板用 {@code {{}}} 语法按需引用，未引用的字段不影响渲染。字段命名与
 * 数据库模板的占位符严格对齐：{@code queryPlanSummary} 对齐
 * product-manager / test-manager / synthesis 模板的 {@code {{queryPlanSummary}}}，
 * 扁平字段 {@code queryPlanType} / {@code queryPlanReasoning}} 等对齐 researcher / impact 模板。
 * {@code answerStyleInstruction} 和 {@code answerStyleRoleKey} 为扁平 String 字段，
 * 替代原 {@code AnswerStyle} 对象属性，使模板不依赖 JavaBean 属性路径。</p>
 */
@Data
@AllArgsConstructor
public class TaskPromptContext {
    private String projectSpaceName;
    private String question;
    /** QuestionPlan.toPromptText() 整段文本，对齐 {{queryPlanSummary}}。 */
    private String queryPlanSummary;
    /** 扁平字段，对齐 researcher-task.md / impact-task.md 的 {{queryPlanType}}。 */
    private String queryPlanType;
    private List<String> queryPlanRecommendedTools;
    private List<String> queryPlanRewrittenQueries;
    private List<String> queryPlanRecommendedSkills;
    private String queryPlanReasoning;
    private List<AgentRequest.RepositoryContext> repositories;
    private String selfLearningContext;
    /** 上游 CODE_RESEARCH 的输出，PRODUCT_REVIEW / TEST_REVIEW 用。 */
    private String researchResult;
    /** 上游 IMPACT_ANALYSIS 的输出，TEST_REVIEW 用。 */
    private String impactResult;
    /** 回答风格指令文本，对齐 {{answerStyleInstruction}}。 */
    private String answerStyleInstruction;
    /** 回答风格角色标识，对齐 {{answerStyleRoleKey}}。 */
    private String answerStyleRoleKey;
    /** ORCHESTRATOR 汇总用，各 Specialist 的输出。 */
    private List<SpecialistResultData> specialistResults;

    /**
     * 创建全空上下文，用于模板语法 dry-run 与默认渲染预览。
     */
    public static TaskPromptContext empty() {
        return new TaskPromptContext(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
