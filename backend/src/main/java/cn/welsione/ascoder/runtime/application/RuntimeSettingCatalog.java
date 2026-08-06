package cn.welsione.ascoder.runtime.application;

import cn.welsione.ascoder.agent.AgentProperties;
import cn.welsione.ascoder.codegraph.CodeGraphProperties;
import cn.welsione.ascoder.common.task.TaskExecutorProperties;
import cn.welsione.ascoder.runtime.domain.SettingValueType;
import cn.welsione.ascoder.runtime.domain.SystemSetting;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时配置白名单：定义可写入的 key、默认值、类型、分类、说明。
 *
 * <p>启动时由 {@link RuntimeSettingsService} 加载到 {@code Map<String, Meta>}；
 * {@code RuntimeSettingsService.write / read*} 仅允许操作白名单中的 key。</p>
 */
public final class RuntimeSettingCatalog {

    /** Agent 行为调参 */
    public static final String CATEGORY_AGENT = "agent";
    /** CodeGraph CLI 调用调参 */
    public static final String CATEGORY_CODEGRAPH = "codegraph";
    /** Git 操作调参 */
    public static final String CATEGORY_GIT = "git";
    /** 异步任务线程池调参 */
    public static final String CATEGORY_TASK = "task";

    private RuntimeSettingCatalog() {
    }

    /**
     * 构建白名单。从各模块 Properties 读取默认值（而非硬编码），保持单一事实源。
     */
    public static Map<String, Meta> buildCatalog(
            AgentProperties agent,
            CodeGraphProperties codegraph,
            cn.welsione.ascoder.repository.git.GitProperties git,
            TaskExecutorProperties taskExecutor) {
        Map<String, Meta> map = new LinkedHashMap<>();
        // Agent
        map.put("agent.max-iters", new Meta("agent.max-iters", agent.getMaxIters(), SettingValueType.INT, CATEGORY_AGENT, "Agent 主循环最大迭代次数"));
        map.put("agent.code-researcher-max-iters", new Meta("agent.code-researcher-max-iters", agent.getCodeResearcherMaxIters(), SettingValueType.INT, CATEGORY_AGENT, "Code Researcher 子 Agent 最大迭代次数"));
        map.put("agent.impact-analyzer-max-iters", new Meta("agent.impact-analyzer-max-iters", agent.getImpactAnalyzerMaxIters(), SettingValueType.INT, CATEGORY_AGENT, "Impact Analyzer 子 Agent 最大迭代次数"));
        map.put("agent.role-specialist-max-iters", new Meta("agent.role-specialist-max-iters", agent.getRoleSpecialistMaxIters(), SettingValueType.INT, CATEGORY_AGENT, "Role Specialist 子 Agent 最大迭代次数"));
        map.put("agent.tool-timeout-seconds", new Meta("agent.tool-timeout-seconds", agent.getToolTimeoutSeconds(), SettingValueType.INT, CATEGORY_AGENT, "工具调用超时（秒）"));
        map.put("agent.model-max-attempts", new Meta("agent.model-max-attempts", agent.getModelMaxAttempts(), SettingValueType.INT, CATEGORY_AGENT, "模型调用最大重试次数"));
        map.put("agent.tool-max-attempts", new Meta("agent.tool-max-attempts", agent.getToolMaxAttempts(), SettingValueType.INT, CATEGORY_AGENT, "工具调用最大重试次数"));
        map.put("agent.planning-enabled", new Meta("agent.planning-enabled", agent.isPlanningEnabled(), SettingValueType.BOOLEAN, CATEGORY_AGENT, "是否启用任务规划"));
        map.put("agent.plan-max-subtasks", new Meta("agent.plan-max-subtasks", agent.getPlanMaxSubtasks(), SettingValueType.INT, CATEGORY_AGENT, "单次规划最多子任务数"));
        map.put("agent.query-planner-enabled", new Meta("agent.query-planner-enabled", agent.isQueryPlannerEnabled(), SettingValueType.BOOLEAN, CATEGORY_AGENT, "是否启用 Query Planner"));
        map.put("agent.query-planner-confidence-threshold", new Meta("agent.query-planner-confidence-threshold", agent.getQueryPlannerConfidenceThreshold(), SettingValueType.DOUBLE, CATEGORY_AGENT, "Query Planner 置信度阈值"));
        map.put("agent.query-planner-ambiguous-threshold", new Meta("agent.query-planner-ambiguous-threshold", agent.getQueryPlannerAmbiguousThreshold(), SettingValueType.DOUBLE, CATEGORY_AGENT, "Query Planner 歧义阈值"));
        map.put("agent.sse-timeout-seconds", new Meta("agent.sse-timeout-seconds", agent.getSseTimeoutSeconds(), SettingValueType.INT, CATEGORY_AGENT, "SSE 连接超时（秒，仅影响新建连接）"));
        map.put("agent.heartbeat-interval-seconds", new Meta("agent.heartbeat-interval-seconds", agent.getHeartbeatIntervalSeconds(), SettingValueType.INT, CATEGORY_AGENT, "SSE 心跳间隔（秒）"));
        map.put("agent.stream-core-threads", new Meta("agent.stream-core-threads", agent.getStreamCoreThreads(), SettingValueType.INT, CATEGORY_AGENT, "流式执行核心线程数（重启生效）"));
        map.put("agent.stream-max-threads", new Meta("agent.stream-max-threads", agent.getStreamMaxThreads(), SettingValueType.INT, CATEGORY_AGENT, "流式执行最大线程数（重启生效）"));
        map.put("agent.stream-queue-capacity", new Meta("agent.stream-queue-capacity", agent.getStreamQueueCapacity(), SettingValueType.INT, CATEGORY_AGENT, "流式执行队列容量（重启生效）"));

        // CodeGraph
        map.put("codegraph.executable", new Meta("codegraph.executable", codegraph.getExecutable(), SettingValueType.STRING, CATEGORY_CODEGRAPH, "CodeGraph CLI 可执行文件路径"));
        map.put("codegraph.timeout-seconds", new Meta("codegraph.timeout-seconds", codegraph.getTimeoutSeconds(), SettingValueType.LONG, CATEGORY_CODEGRAPH, "CodeGraph 命令超时（秒）"));
        map.put("codegraph.index-timeout-seconds", new Meta("codegraph.index-timeout-seconds", codegraph.getIndexTimeoutSeconds(), SettingValueType.LONG, CATEGORY_CODEGRAPH, "CodeGraph 索引超时（秒）"));

        // Git
        map.put("git.timeout-seconds", new Meta("git.timeout-seconds", git.getTimeoutSeconds(), SettingValueType.LONG, CATEGORY_GIT, "Git 命令超时（秒）"));

        // 异步任务线程池（修改需重启进程生效）
        putTaskPool(map, "git-clone", taskExecutor.getGitCloneCoreThreads(), taskExecutor.getGitCloneMaxThreads(), taskExecutor.getGitCloneQueueCapacity(), "Git 克隆");
        putTaskPool(map, "git-fetch", taskExecutor.getGitFetchCoreThreads(), taskExecutor.getGitFetchMaxThreads(), taskExecutor.getGitFetchQueueCapacity(), "Git 同步");
        putTaskPool(map, "codegraph-index", taskExecutor.getCodegraphIndexCoreThreads(), taskExecutor.getCodegraphIndexMaxThreads(), taskExecutor.getCodegraphIndexQueueCapacity(), "CodeGraph 索引");
        putTaskPool(map, "codegraph-sync", taskExecutor.getCodegraphSyncCoreThreads(), taskExecutor.getCodegraphSyncMaxThreads(), taskExecutor.getCodegraphSyncQueueCapacity(), "CodeGraph 同步");
        putTaskPool(map, "project-space-prepare", taskExecutor.getProjectSpacePrepareCoreThreads(), taskExecutor.getProjectSpacePrepareMaxThreads(), taskExecutor.getProjectSpacePrepareQueueCapacity(), "项目空间准备");
        putTaskPool(map, "branch-refresh", taskExecutor.getBranchRefreshCoreThreads(), taskExecutor.getBranchRefreshMaxThreads(), taskExecutor.getBranchRefreshQueueCapacity(), "分支刷新");

        return map;
    }

    /**
     * 为指定任务类型注册 core / max / queue 三项线程池配置。
     */
    private static void putTaskPool(Map<String, Meta> map, String kindKey, int core, int max, int queue, String label) {
        map.put("task." + kindKey + "-core-threads",
                new Meta("task." + kindKey + "-core-threads", core, SettingValueType.INT, CATEGORY_TASK, label + "核心线程数（重启生效）"));
        map.put("task." + kindKey + "-max-threads",
                new Meta("task." + kindKey + "-max-threads", max, SettingValueType.INT, CATEGORY_TASK, label + "最大线程数（重启生效）"));
        map.put("task." + kindKey + "-queue-capacity",
                new Meta("task." + kindKey + "-queue-capacity", queue, SettingValueType.INT, CATEGORY_TASK, label + "队列容量（重启生效）"));
    }

    @Getter
    @AllArgsConstructor
    public static class Meta {
        private final String key;
        private final Object defaultValue;
        private final SettingValueType valueType;
        private final String category;
        private final String description;

        public List<String> allowedCategories() {
            return List.of(CATEGORY_AGENT, CATEGORY_CODEGRAPH, CATEGORY_GIT, CATEGORY_TASK);
        }
    }
}