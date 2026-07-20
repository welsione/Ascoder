package cn.welsione.ascoder.agent.infrastructure.prompt;

import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.jprompt.JPrompt;
import cn.welsione.jprompt.JPromptFactory;
import cn.welsione.jprompt.TemplateException;
import cn.welsione.jprompt.loader.ClasspathTemplateLoader;
import cn.welsione.jprompt.loader.CompositeTemplateLoader;
import cn.welsione.jprompt.loader.TemplateLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 提示词统一管理，环境感知双源加载。
 *
 * <p>所有提示词通过此类获取，禁止消费方直接调用 {@link JPrompt}。
 * 根据 {@code ascoder.prompt.source} 配置决定加载优先级：
 * <ul>
 *   <li>{@code file}（开发环境）：classpath 文件优先，数据库兜底</li>
 *   <li>{@code db}（生产环境）：数据库优先，classpath 文件兜底</li>
 * </ul>
 * 框架提示词（工具输出模板、回答风格、Planner 模板）始终从 classpath 加载，不参与双源切换。</p>
 */
@Slf4j
@Component
public class PromptManager {

    /** agentId → 系统提示词 classpath 路径映射 */
    private static final Map<String, String> SYSTEM_PATH_MAP = Map.of(
            "self-learning-insight", "prompts/self-learning-insight-system.md",
            "self-learning-insight-review", "prompts/self-learning-insight-review-verify-system.md",
            "self-learning-insight-refine", "prompts/self-learning-insight-refine-system.md"
    );

    /** agentId → 任务模板 classpath 路径映射 */
    private static final Map<String, String> TASK_PATH_MAP = Map.of(
            "self-learning-insight", "prompts/self-learning-insight-task.md",
            "self-learning-insight-review", "prompts/self-learning-insight-review-verify-task.md",
            "self-learning-insight-refine", "prompts/self-learning-insight-refine-task.md"
    );

    private final AgentConfigService agentConfigService;
    private final JPromptFactory jPromptFactory;
    private final boolean dbFirst;

    public PromptManager(AgentConfigService agentConfigService,
                         CompositeTemplateLoader compositeLoader,
                         boolean dbFirst) {
        this.agentConfigService = agentConfigService;
        this.jPromptFactory = JPromptFactory.builder()
                .loader(compositeLoader)
                .cacheEnabled(true)
                .build();
        this.dbFirst = dbFirst;
        log.info("PromptManager 初始化，dbFirst={}", dbFirst);
    }

    /**
     * 获取 Agent 系统提示词（环境感知双源）。
     *
     * <p>db 模式：数据库优先，classpath 兜底。file 模式：classpath 优先，数据库兜底。</p>
     */
    public String getSystemPrompt(String agentId) {
        String classpathPath = classpathSystemPath(agentId);

        if (dbFirst) {
            String dbResult = loadSystemFromDb(agentId);
            if (dbResult != null) {
                return dbResult;
            }
            return loadFromClasspath(classpathPath, "systemPrompt", agentId);
        }

        String fileResult = loadFromClasspath(classpathPath, "systemPrompt", agentId);
        if (fileResult != null) {
            return fileResult;
        }
        return loadSystemFromDb(agentId);
    }

    /**
     * 渲染 Agent 任务模板（环境感知双源）。
     */
    public <T> String renderTaskTemplate(String agentId, Class<T> dataClass, T data) {
        String classpathPath = classpathTaskPath(agentId);

        if (dbFirst) {
            String dbResult = renderTaskFromDb(agentId, dataClass, data);
            if (dbResult != null) {
                return dbResult;
            }
            return renderTaskFromClasspath(classpathPath, dataClass, data, agentId);
        }

        String fileResult = renderTaskFromClasspath(classpathPath, dataClass, data, agentId);
        if (fileResult != null) {
            return fileResult;
        }
        return renderTaskFromDb(agentId, dataClass, data);
    }

    /**
     * 渲染框架提示词（始终 classpath，不参与双源切换）。
     */
    public <T> String renderFramework(String path, Class<T> dataClass, T data) {
        return JPrompt.template(path, dataClass).build(data);
    }

    /**
     * 获取框架静态提示词（始终 classpath，不参与双源切换）。
     */
    public String getFramework(String path) {
        return JPrompt.get(path).get();
    }

    /**
     * 获取 Skill 内容（环境感知双源）。
     *
     * <p>file 模式使用 classpath，db 模式当前仍回退 classpath（预留接口）。</p>
     */
    public String getSkillContent(String skillName) {
        // skillName 使用下划线（如 spring_boot_analysis），文件名使用短横线（如 spring-boot-analysis.md）
        String fileName = skillName.replace('_', '-');
        String classpathPath = "prompts/skills/" + fileName + ".md";
        if (dbFirst) {
            // 预留：db 模式查数据库获取 skill 内容，当前回退 classpath
            log.debug("db 模式 Skill 内容获取，skillName={}，当前回退 classpath", skillName);
        }
        try {
            return JPrompt.get(classpathPath).get();
        } catch (TemplateException ex) {
            throw new TemplateException("Skill 内容加载失败，skillName=" + skillName + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * 获取回答风格指令（始终 classpath，不参与双源切换）。
     */
    public String getAnswerStyle(String roleKey) {
        return JPrompt.get("prompts/answer-style-" + roleKey + ".md").get();
    }

    /**
     * 获取 agentId 对应的系统提示词 classpath 路径。
     *
     * @return classpath 路径，无映射时返回 null（仅数据库源）
     */
    String classpathSystemPath(String agentId) {
        return SYSTEM_PATH_MAP.get(agentId);
    }

    /**
     * 获取 agentId 对应的任务模板 classpath 路径。
     *
     * @return classpath 路径，无映射时返回 null（仅数据库源）
     */
    String classpathTaskPath(String agentId) {
        return TASK_PATH_MAP.get(agentId);
    }

    private String loadSystemFromDb(String agentId) {
        Optional<AgentConfig> config = agentConfigService.getByAgentId(agentId);
        if (config.isPresent()) {
            String systemPrompt = config.get().getSystemPrompt();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                log.debug("从数据库加载系统提示词，agentId={}", agentId);
                return systemPrompt;
            }
        }
        return null;
    }

    private String loadFromClasspath(String path, String fieldType, String agentId) {
        if (path == null) {
            return null;
        }
        try {
            String content = jPromptFactory.get(path).get();
            log.debug("从 classpath 加载 {}，agentId={}，path={}", fieldType, agentId, path);
            return content;
        } catch (TemplateException ex) {
            log.debug("classpath 加载 {} 失败，agentId={}，path={}：{}", fieldType, agentId, path, ex.getMessage());
            return null;
        }
    }

    private <T> String renderTaskFromDb(String agentId, Class<T> dataClass, T data) {
        Optional<AgentConfig> config = agentConfigService.getByAgentId(agentId);
        if (config.isPresent()) {
            String taskTemplate = config.get().getTaskTemplate();
            if (taskTemplate != null && !taskTemplate.isBlank()) {
                log.debug("从数据库渲染任务模板，agentId={}", agentId);
                return JPrompt.templateInline(taskTemplate, dataClass).build(data);
            }
        }
        return null;
    }

    private <T> String renderTaskFromClasspath(String path, Class<T> dataClass, T data, String agentId) {
        if (path == null) {
            return null;
        }
        try {
            String content = jPromptFactory.template(path, dataClass).build(data);
            log.debug("从 classpath 渲染任务模板，agentId={}，path={}", agentId, path);
            return content;
        } catch (TemplateException ex) {
            log.debug("classpath 渲染任务模板失败，agentId={}，path={}：{}", agentId, path, ex.getMessage());
            return null;
        }
    }
}
