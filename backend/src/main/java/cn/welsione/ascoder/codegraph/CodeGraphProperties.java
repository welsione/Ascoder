package cn.welsione.ascoder.codegraph;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CodeGraph 调参默认值，对应 application.yml 中 {@code ascoder.codegraph.*} 节点。
 *
 * <p>{@code executable} 是启动期依赖（决定 Bean 装配时拼出哪条 CLI 命令），仍走环境变量；
 * 超时相关字段可在设置页运行时热改。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ascoder.codegraph")
public class CodeGraphProperties {

    private String executable = "codegraph";
    private long timeoutSeconds = 300;
    private long indexTimeoutSeconds = 3600;
}