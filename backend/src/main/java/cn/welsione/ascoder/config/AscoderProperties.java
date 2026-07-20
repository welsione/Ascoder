package cn.welsione.ascoder.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ascoder 顶层配置属性，作为运行时参数的默认值来源。
 *
 * <p>本类仅承载「启动期 / 装配期」以及「运行时默认值」；运行时实际生效的值由
 * {@code cn.welsione.ascoder.runtime.application.RuntimeSettingsService}
 * 读取（DB 优先，环境变量兜底）。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ascoder")
public class AscoderProperties {

    /** 加密密钥（Base64 编码 32 字节 AES-256 密钥）。 */
    private String encryptionKey = "";

    /** Git 仓库根目录。 */
    private String repoRoot = "./data/repos";

    /** Git worktree 根目录。 */
    private String worktreeRoot = "./data/worktrees";

    /** 项目空间根目录。 */
    private String projectSpaceRoot = "./data/project-spaces";

    /** CodeGraph 索引根目录。 */
    private String codegraphRoot = "./data/codegraph";

    /** LLM 供应商模式：database / agentscope（决定装配哪个 ChatModelFactory）。 */
    private String llmProvider = "database";

    /** Prompt 来源：file / db。 */
    private Prompt prompt = new Prompt();

    @Getter
    @Setter
    public static class Prompt {
        private String source = "file";
    }
}