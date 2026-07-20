package cn.welsione.ascoder.repository.git;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Git 调参默认值，对应 application.yml 中 {@code ascoder.git.*} 节点。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ascoder.git")
public class GitProperties {

    private long timeoutSeconds = 120;
    private String httpProxy = "";
    private String tlsProxy = "";
}