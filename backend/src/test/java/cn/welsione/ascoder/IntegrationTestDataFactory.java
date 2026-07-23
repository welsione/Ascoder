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
import cn.welsione.ascoder.repository.RepositoryBranch;
import cn.welsione.ascoder.repository.RepositoryBranchJpaRepository;
import cn.welsione.ascoder.repository.RepositoryBranchSourceKind;
import cn.welsione.ascoder.repository.RepositoryStatus;
import cn.welsione.ascoder.repository.project.Project;
import cn.welsione.ascoder.repository.project.ProjectJpaRepository;
import cn.welsione.ascoder.repository.project.ProjectRepository;
import cn.welsione.ascoder.repository.project.ProjectRepositoryJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMember;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMemberJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceStatus;
import cn.welsione.ascoder.repository.workspace.BranchWorkspace;
import cn.welsione.ascoder.repository.workspace.BranchWorkspaceJpaRepository;
import cn.welsione.ascoder.repository.workspace.BranchWorkspaceStatus;
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

    @Autowired
    private ProjectRepositoryJpaRepository projectMemberRepository;

    @Autowired
    private ProjectSpaceMemberJpaRepository projectSpaceMemberRepository;

    @Autowired
    private RepositoryBranchJpaRepository branchRepository;

    @Autowired
    private BranchWorkspaceJpaRepository branchWorkspaceRepository;

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

    /** 创建关联指定 LlmProvider 的 AgentConfig，返回带 id 的实体。 */
    public AgentConfig createAgentConfigWithLlmProvider(String agentId, Long llmProviderId) {
        AgentConfig config = createAgentConfig(agentId);
        config.setLlmProviderId(llmProviderId);
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

    /**
     * 向项目添加仓库成员，创建 ProjectRepository 关联记录。
     *
     * @param project    所属项目
     * @param repository 仓库实体
     * @param alias      目录别名
     * @return 带有 id 的 ProjectRepository
     */
    public ProjectRepository createProjectMember(Project project, CodeRepository repository, String alias) {
        ProjectRepository member = new ProjectRepository();
        member.setProject(project);
        member.setRepository(repository);
        member.setAlias(alias);
        member.setRole("repository");
        member.setPrimaryRepository(false);
        member.setSortOrder(0);
        return projectMemberRepository.save(member);
    }

    /**
     * 创建仓库分支引用，缓存 Git 分支发现结果。
     *
     * @param repository 所属仓库
     * @param branchName 分支名称
     * @param refName    引用全名（如 refs/heads/main）
     * @param commitSha  提交 SHA
     * @return 带有 id 的 RepositoryBranch
     */
    public RepositoryBranch createRepositoryBranch(CodeRepository repository, String branchName,
                                                   String refName, String commitSha) {
        return createRepositoryBranch(repository, branchName, refName, commitSha,
                RepositoryBranchSourceKind.LOCAL_HEAD);
    }

    /**
     * 创建指定来源类型的仓库分支引用。
     */
    public RepositoryBranch createRepositoryBranch(CodeRepository repository, String branchName,
                                                   String refName, String commitSha,
                                                   RepositoryBranchSourceKind sourceKind) {
        RepositoryBranch branch = new RepositoryBranch();
        branch.setRepository(repository);
        branch.setName(branchName);
        branch.setRefName(refName);
        branch.setCommitSha(commitSha);
        branch.setSourceKind(sourceKind);
        branch.setActive(true);
        return branchRepository.save(branch);
    }

    /**
     * 创建分支工作区实体（READY 状态），用于测试 workspace 查询和索引。
     *
     * @param repository   所属仓库
     * @param branchName   分支名称
     * @param commitSha    提交 SHA
     * @param worktreePath worktree 相对路径
     * @return 带有 id 的 BranchWorkspace
     */
    public BranchWorkspace createBranchWorkspace(CodeRepository repository, String branchName,
                                                  String commitSha, String worktreePath) {
        BranchWorkspace workspace = new BranchWorkspace();
        workspace.setRepository(repository);
        workspace.setBranchName(branchName);
        workspace.setCommitSha(commitSha);
        workspace.setCommitMessage("测试提交");
        workspace.setWorktreePath(worktreePath);
        workspace.setCodegraphIndexPath(worktreePath + "/.codegraph");
        workspace.setStatus(BranchWorkspaceStatus.READY);
        return branchWorkspaceRepository.save(workspace);
    }

    /**
     * 创建项目空间成员，关联仓库分支和分支工作区。
     *
     * @param space          所属项目空间
     * @param repository     成员仓库
     * @param branch         仓库分支（可空）
     * @param branchWorkspace 分支工作区（可空）
     * @param branchName     分支名称
     * @param alias          目录别名
     * @return 带有 id 的 ProjectSpaceMember
     */
    public ProjectSpaceMember createProjectSpaceMember(ProjectSpace space, CodeRepository repository,
                                                       RepositoryBranch branch, BranchWorkspace branchWorkspace,
                                                       String branchName, String alias) {
        ProjectSpaceMember member = new ProjectSpaceMember();
        member.setProjectSpace(space);
        member.setRepository(repository);
        member.setBranch(branch);
        member.setBranchWorkspace(branchWorkspace);
        member.setBranchName(branchName);
        if (branch != null) {
            member.setBranchRefName(branch.getRefName());
            member.setBranchSourceKind(branch.getSourceKind());
            member.setCommitSha(branch.getCommitSha());
        }
        member.setAlias(alias);
        member.setRole("repository");
        member.setLinkPath(space.getRootPath() + "/" + alias);
        return projectSpaceMemberRepository.save(member);
    }
}
