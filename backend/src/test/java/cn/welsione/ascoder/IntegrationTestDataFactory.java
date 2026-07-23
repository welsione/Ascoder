package cn.welsione.ascoder;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.persistence.AgentConfigJpaRepository;
import cn.welsione.ascoder.question.domain.Conversation;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.question.persistence.ConversationJpaRepository;
import cn.welsione.ascoder.question.persistence.QueryPlanJpaRepository;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import cn.welsione.ascoder.question.planning.QuestionType;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.CodeRepositoryJpaRepository;
import cn.welsione.ascoder.repository.RepositoryStatus;
import cn.welsione.ascoder.repository.project.Project;
import cn.welsione.ascoder.repository.project.ProjectJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 集成测试数据工厂：集中创建关联实体（AgentConfig/CodeRepository/Conversation/Question/QueryPlan 等），
 * 满足外键约束，避免每个测试重复准备。
 *
 * <p>需配合 {@code @Transactional} 测试方法使用，结束后自动回滚。</p>
 */
@Component
public class IntegrationTestDataFactory {

    @Autowired
    private AgentConfigJpaRepository agentConfigRepository;

    @Autowired
    private CodeRepositoryJpaRepository codeRepositoryRepository;

    @Autowired
    private ConversationJpaRepository conversationRepository;

    @Autowired
    private QuestionJpaRepository questionRepository;

    @Autowired
    private QueryPlanJpaRepository queryPlanRepository;

    @Autowired
    private ProjectJpaRepository projectRepository;

    @Autowired
    private ProjectSpaceJpaRepository projectSpaceRepository;

    /** 创建最小可用 AgentConfig，返回带 id 的实体。 */
    public AgentConfig createAgentConfig(String agentId) {
        AgentConfig config = new AgentConfig();
        config.setAgentId(agentId);
        config.setDisplayName("测试-" + agentId);
        config.setAgentRole(AgentRole.ORCHESTRATOR);
        config.setSystemPrompt("测试系统提示词");
        config.setMaxIters(12);
        return agentConfigRepository.save(config);
    }

    /** 创建最小可用 CodeRepository，返回带 id 的实体。 */
    public CodeRepository createRepository(String name, String localPath) {
        CodeRepository repo = new CodeRepository();
        repo.setName(name);
        repo.setLocalPath(localPath);
        repo.setStatus(RepositoryStatus.CREATED);
        return codeRepositoryRepository.save(repo);
    }

    /** 创建最小可用 Conversation，返回带 id 的实体。 */
    public Conversation createConversation(String title) {
        Conversation conversation = new Conversation();
        conversation.setTitle(title);
        return conversationRepository.save(conversation);
    }

    /** 创建最小可用 Question（PENDING 状态），关联指定 Conversation。 */
    public Question createQuestion(Conversation conversation, String text) {
        return createQuestion(conversation, text, QuestionStatus.PENDING);
    }

    /** 创建指定状态的 Question，关联指定 Conversation。 */
    public Question createQuestion(Conversation conversation, String text, QuestionStatus status) {
        Question question = new Question();
        question.setConversation(conversation);
        question.setText(text);
        question.setStatus(status);
        return questionRepository.save(question);
    }

    /** 创建最小可用 QueryPlan，关联指定 Question。 */
    public QueryPlan createQueryPlan(Question question) {
        QueryPlan plan = new QueryPlan();
        plan.setQuestion(question);
        plan.setType(QuestionType.GENERAL_EXPLANATION);
        plan.setRewrittenQueriesJson("[]");
        plan.setRecommendedToolsJson("[]");
        plan.setConfidence(0.9);
        return queryPlanRepository.save(plan);
    }

    /** 创建最小可用 Project，返回带 id 的实体。 */
    public Project createProject(String name) {
        Project project = new Project();
        project.setName(name);
        return projectRepository.save(project);
    }

    /** 创建最小可用 ProjectSpace（CREATED 状态），关联指定 Project，返回带 id 的实体。 */
    public ProjectSpace createProjectSpace(Project project, String name) {
        ProjectSpace space = new ProjectSpace();
        space.setProject(project);
        space.setName(name);
        space.setRootPath("test-" + name);
        space.setStatus(ProjectSpaceStatus.CREATED);
        return projectSpaceRepository.save(space);
    }
}
